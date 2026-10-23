// Farm Lab: any number of virtual sensors and actuators that behave like a small farm, plus
// scenarios (drought, heat wave, broken pump, power cut...) that check how the server reacts.
// Devices talk to the server through the same device API and device key as a real ESP32.
// Shared by the Farm Lab page and scripts/farm-scenarios.mjs, so it has no imports.

export type LabSensorType = "SOIL_MOISTURE" | "TEMPERATURE" | "FEED_LEVEL";
export type LabActuatorType = "WATER_PUMP" | "FAN" | "FEEDER";

export interface LabSensor {
  id: number;
  name: string;
  type: LabSensorType;
  zoneId: number;
  value: number;
  /** false = unplugged: sends nothing */
  sending: boolean;
  lastStatus: number | null;
}

export interface LabActuator {
  id: number;
  name: string;
  type: LabActuatorType;
  zoneId: number;
  relayOn: boolean;
  /** true = the relay switches but nothing happens (clogged pipe, burnt motor) */
  noEffect: boolean;
}

/** The farm's surroundings, applied on every tick. */
export interface Weather {
  label: string;
  evaporation: number; // soil % lost per tick
  rain: number; // soil % gained per tick
  ambient: number; // °C the air drifts towards
  feedUse: number; // feed % eaten per tick
}

export const NORMAL_WEATHER: Weather = { label: "Normal day", evaporation: 0.3, rain: 0, ambient: 28, feedUse: 0.3 };

/** Healthy starting values, between the default rule thresholds. */
export const HEALTHY: Record<LabSensorType, number> = { SOIL_MOISTURE: 55, TEMPERATURE: 28, FEED_LEVEL: 60 };

const RANGE: Record<LabSensorType, [number, number]> = {
  SOIL_MOISTURE: [0, 100],
  TEMPERATURE: [-10, 60],
  FEED_LEVEL: [0, 100],
};

export interface LabAlert {
  message: string;
  severity: string;
  lastRaisedAt: string;
}

/** What the lab needs from the outside world; the page and the command-line runner each provide one. */
export interface LabIO {
  /** POST /api/devices/readings; returns the HTTP status (-1 = server not reachable) */
  sendReading(sensorId: number, value: number): Promise<number>;
  /** GET /api/devices/actuators/{id}/state; null = no answer */
  relayState(actuatorId: number): Promise<boolean | null>;
  /** Open alerts, read with a signed-in user's token */
  openAlerts(): Promise<LabAlert[]>;
  /** Technician lifecycle action, e.g. FAULT, START_REPAIR, COMPLETE_REPAIR */
  lifecycle(actuatorId: number, action: string, body?: Record<string, unknown>): Promise<void>;
  sleep(ms: number): Promise<void>;
  log(text: string, tone?: "ok" | "warn" | "error" | "info"): void;
}

export type Verdict = "pass" | "fail" | "gap" | "waiting";

/** What the lab saw since the scenario started. */
export interface Observed {
  elapsedSec: number;
  startedAt: number;
  turnedOn: Set<number>; // actuator ids whose relay switched on
  turnedOffAfterOn: Set<number>;
  rejected: number; // 422 answers
  alerts: LabAlert[]; // open alerts raised or repeated since the start
}

export interface Scenario {
  id: string;
  title: string;
  /** What happens on the farm, in plain words */
  story: string;
  /** What the system should do */
  expect: string;
  timeoutSec: number;
  /** A gap the system cannot detect yet; the scenario shows it on purpose */
  knownGap?: string;
  setup(lab: FarmLab): Promise<void> | void;
  check(lab: FarmLab, o: Observed): Verdict;
  cleanup?(lab: FarmLab): Promise<void> | void;
}

export interface ScenarioResult {
  id: string;
  title: string;
  verdict: Exclude<Verdict, "waiting">;
  seconds: number;
  note: string;
}

const first = <T>(list: T[], pred: (x: T) => boolean) => list.find(pred);

export class FarmLab {
  sensors: LabSensor[] = [];
  actuators: LabActuator[] = [];
  weather: Weather = { ...NORMAL_WEATHER };
  running = false;
  tickCount = 0;
  current: { scenario: Scenario; observed: Observed } | null = null;
  results: ScenarioResult[] = [];
  io: LabIO;
  tickMs: number;
  private stopRequested = false;

  constructor(io: LabIO, tickMs = 5000) {
    this.io = io;
    this.tickMs = tickMs;
  }

  addSensor(s: Omit<LabSensor, "value" | "sending" | "lastStatus"> & { value?: number }) {
    if (this.sensors.some((x) => x.id === s.id)) return;
    this.sensors.push({ ...s, value: s.value ?? HEALTHY[s.type], sending: true, lastStatus: null });
  }

  addActuator(a: Omit<LabActuator, "relayOn" | "noEffect">) {
    if (this.actuators.some((x) => x.id === a.id)) return;
    this.actuators.push({ ...a, relayOn: false, noEffect: false });
  }

  remove(kind: "sensor" | "actuator", id: number) {
    if (kind === "sensor") this.sensors = this.sensors.filter((s) => s.id !== id);
    else this.actuators = this.actuators.filter((a) => a.id !== id);
  }

  sensor(type: LabSensorType) {
    return first(this.sensors, (s) => s.type === type);
  }

  actuator(type: LabActuatorType) {
    return first(this.actuators, (a) => a.type === type);
  }

  /** Back to a calm, healthy farm. */
  resetFarm() {
    this.weather = { ...NORMAL_WEATHER };
    for (const s of this.sensors) {
      s.value = HEALTHY[s.type];
      s.sending = true;
    }
    for (const a of this.actuators) a.noEffect = false;
  }

  /** One step of farm physics: weather, plus whatever the actuators in the same zone are doing. */
  physics() {
    const w = this.weather;
    const working = (type: LabActuatorType, zoneId: number) =>
      this.actuators.some((a) => a.type === type && a.zoneId === zoneId && a.relayOn && !a.noEffect);
    for (const s of this.sensors) {
      const noise = (Math.random() - 0.5) * 0.2;
      if (s.type === "SOIL_MOISTURE") {
        s.value += -w.evaporation + w.rain + (working("WATER_PUMP", s.zoneId) ? 4 : 0) + noise;
      } else if (s.type === "TEMPERATURE") {
        s.value += (w.ambient - s.value) * 0.15 - (working("FAN", s.zoneId) ? 1.2 : 0) + noise;
      } else {
        s.value += -w.feedUse + (working("FEEDER", s.zoneId) ? 5 : 0) + noise / 2;
      }
      const [lo, hi] = RANGE[s.type];
      s.value = Math.min(hi, Math.max(lo, s.value));
    }
  }

  /** One device cycle: move the farm, send every plugged-in sensor, ask for every relay. */
  async tick() {
    this.tickCount++;
    this.physics();
    for (const s of this.sensors) {
      if (!s.sending) continue;
      const v = Math.round(s.value * 10) / 10;
      const status = await this.io.sendReading(s.id, v);
      s.lastStatus = status;
      if (status === 422 && this.current) this.current.observed.rejected++;
      if (status !== 201) this.io.log(`${s.name} = ${v} -> HTTP ${status}`, status === 422 ? "warn" : "error");
    }
    for (const a of this.actuators) {
      const on = await this.io.relayState(a.id);
      const now = on === true; // no answer = off (fail-safe, like the firmware)
      if (now !== a.relayOn) {
        this.io.log(`${a.name} relay ${now ? "ON" : "OFF"}${on === null ? " (no answer, fail-safe)" : ""}`, now ? "ok" : "info");
        if (this.current) (now ? this.current.observed.turnedOn : this.current.observed.turnedOffAfterOn).add(a.id);
      }
      a.relayOn = now;
    }
  }

  /** Runs devices continuously until stop() (used by the page). */
  async run() {
    if (this.running) return;
    this.running = true;
    this.stopRequested = false;
    while (!this.stopRequested) {
      await this.tick();
      await this.io.sleep(this.tickMs);
    }
    this.running = false;
  }

  stop() {
    this.stopRequested = true;
  }

  /** Sends one impossible value right away (a broken or miswired sensor). */
  async sendRaw(sensorId: number, value: number) {
    const status = await this.io.sendReading(sensorId, value);
    if (status === 422 && this.current) this.current.observed.rejected++;
    this.io.log(`Sensor ${sensorId} = ${value} -> HTTP ${status}${status === 422 ? " (rejected as impossible)" : ""}`, status === 422 ? "warn" : "info");
    return status;
  }

  /**
   * Runs one scenario: set it up, keep the devices going, and check the server's reaction
   * until it passes or the time is up. Devices must be running (run()) or ticked by the caller.
   */
  async runScenario(scenario: Scenario, ownLoop = true): Promise<ScenarioResult> {
    const startedAt = Date.now();
    const observed: Observed = {
      elapsedSec: 0, startedAt, turnedOn: new Set(), turnedOffAfterOn: new Set(), rejected: 0, alerts: [],
    };
    this.current = { scenario, observed };
    this.io.log(`▶ ${scenario.title}: ${scenario.story}`, "info");
    let verdict: Verdict = "waiting";
    try {
      await scenario.setup(this);
      while (verdict === "waiting") {
        if (ownLoop) await this.tick();
        observed.elapsedSec = (Date.now() - startedAt) / 1000;
        const alerts = await this.io.openAlerts();
        // Only alerts about the lab's own devices, raised or repeated since the start
        const names = [...this.sensors, ...this.actuators].map((d) => d.name);
        observed.alerts = alerts.filter((a) => Date.parse(a.lastRaisedAt) >= startedAt - 1000 && names.some((n) => a.message.includes(n)));
        verdict = scenario.check(this, observed);
        if (verdict === "waiting" && observed.elapsedSec >= scenario.timeoutSec) verdict = "fail";
        if (verdict === "waiting") await this.io.sleep(ownLoop ? this.tickMs : 1000);
      }
    } finally {
      await scenario.cleanup?.(this);
      this.current = null;
    }
    const result: ScenarioResult = {
      id: scenario.id,
      title: scenario.title,
      verdict,
      seconds: Math.round((Date.now() - startedAt) / 1000),
      note: verdict === "gap" ? scenario.knownGap ?? "" : verdict === "pass" ? scenario.expect : `Expected: ${scenario.expect}`,
    };
    this.results = [...this.results.filter((r) => r.id !== scenario.id), result];
    const mark = { pass: "✔ PASS", fail: "✘ FAIL", gap: "◆ KNOWN GAP" }[result.verdict];
    this.io.log(`${mark} ${scenario.title} (${result.seconds} s)`, result.verdict === "fail" ? "error" : result.verdict === "gap" ? "warn" : "ok");
    return result;
  }

  /**
   * Between scenarios: healthy farm, every relay off, and long enough since the last start
   * that rule cooldowns (1 minute in the lab) no longer block the next scenario.
   */
  async settle(ownLoop = true, minSeconds = 65) {
    this.resetFarm();
    const start = Date.now();
    this.io.log("… farm back to normal, waiting for every relay to switch off", "info");
    while (Date.now() - start < 240_000) {
      if (ownLoop) await this.tick();
      if ((Date.now() - start) / 1000 >= minSeconds && this.actuators.every((a) => !a.relayOn)) return;
      await this.io.sleep(ownLoop ? this.tickMs : 1000);
    }
  }

  async runAll(list: Scenario[] = SCENARIOS, ownLoop = true) {
    this.results = [];
    for (let i = 0; i < list.length; i++) {
      await this.runScenario(list[i]!, ownLoop);
      if (i < list.length - 1) await this.settle(ownLoop);
    }
    return this.results;
  }
}

// ---------- scenarios ----------

const alertWith = (o: Observed, text: string, severity?: string) =>
  o.alerts.some((a) => a.message.includes(text) && (!severity || a.severity === severity));

function needs(lab: FarmLab, sensor: LabSensorType, actuator?: LabActuatorType) {
  const s = lab.sensor(sensor);
  const a = actuator ? lab.actuator(actuator) : undefined;
  if (!s || (actuator && !a)) {
    throw new Error(`This scenario needs a ${sensor.toLowerCase().replace("_", " ")} sensor${actuator ? ` and a ${actuator.toLowerCase().replace("_", " ")}` : ""} in the lab`);
  }
  return { s, a: a as LabActuator };
}

export const SCENARIOS: Scenario[] = [
  {
    id: "normal",
    title: "Normal day",
    story: "Mild weather, enough water and feed.",
    expect: "No pump, fan or feeder starts and no new alert for 45 s",
    timeoutSec: 60,
    setup: (lab) => lab.resetFarm(),
    check: (lab, o) => {
      if (o.turnedOn.size > 0) return "fail";
      if (o.alerts.length > 0) return "fail";
      return o.elapsedSec >= 45 ? "pass" : "waiting";
    },
  },
  {
    id: "drought",
    title: "Dry spell",
    story: "Hot wind dries the vegetable bed quickly.",
    expect: "The water pump starts by itself before the soil gets too dry",
    timeoutSec: 150,
    setup: (lab) => {
      const { s } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      s.value = 36;
      lab.weather = { ...NORMAL_WEATHER, label: "Dry spell", evaporation: 2 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("WATER_PUMP")!.id) ? "pass" : "waiting"),
  },
  {
    id: "rain",
    title: "Heavy rain",
    story: "A storm soaks the vegetable bed.",
    expect: "The pump stays off; no water is wasted",
    timeoutSec: 60,
    setup: (lab) => {
      const { s } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      s.value = 50;
      lab.weather = { ...NORMAL_WEATHER, label: "Heavy rain", evaporation: 0, rain: 3 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("WATER_PUMP")!.id) ? "fail" : o.elapsedSec >= 45 ? "pass" : "waiting"),
  },
  {
    id: "heatwave",
    title: "Heat wave",
    story: "The afternoon sun heats the chicken house towards 39 °C.",
    expect: "The exhaust fan starts above 32 °C",
    timeoutSec: 150,
    setup: (lab) => {
      needs(lab, "TEMPERATURE", "FAN");
      lab.weather = { ...NORMAL_WEATHER, label: "Heat wave", ambient: 39 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("FAN")!.id) ? "pass" : "waiting"),
  },
  {
    id: "cold-night",
    title: "Cool night",
    story: "The temperature drops to 21 °C after sunset.",
    expect: "The fan stays off",
    timeoutSec: 60,
    setup: (lab) => {
      needs(lab, "TEMPERATURE", "FAN");
      lab.weather = { ...NORMAL_WEATHER, label: "Cool night", ambient: 21 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("FAN")!.id) ? "fail" : o.elapsedSec >= 45 ? "pass" : "waiting"),
  },
  {
    id: "feed-out",
    title: "Feed running out",
    story: "The chickens eat faster than usual and the tray empties.",
    expect: "The feeder refills the tray below 20 %",
    timeoutSec: 150,
    setup: (lab) => {
      const { s } = needs(lab, "FEED_LEVEL", "FEEDER");
      s.value = 27;
      lab.weather = { ...NORMAL_WEATHER, label: "Hungry flock", feedUse: 2 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("FEEDER")!.id) ? "pass" : "waiting"),
  },
  {
    id: "broken-pump",
    title: "Broken pump",
    story: "The technician has reported the pump as faulty, and then the soil dries out.",
    expect: "A CRITICAL alert says the pump could NOT start, so a person waters by hand",
    timeoutSec: 150,
    async setup(lab) {
      const { s, a } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      await lab.io.lifecycle(a.id, "FAULT");
      s.value = 36;
      lab.weather = { ...NORMAL_WEATHER, label: "Dry spell", evaporation: 2 };
    },
    check: (lab, o) => (alertWith(o, `${lab.actuator("WATER_PUMP")!.name} could NOT start`, "CRITICAL") ? "pass" : "waiting"),
    async cleanup(lab) {
      const a = lab.actuator("WATER_PUMP");
      if (!a) return;
      await lab.io.lifecycle(a.id, "START_REPAIR").catch(() => undefined);
      await lab.io.lifecycle(a.id, "COMPLETE_REPAIR", { testPassed: true }).catch(() => undefined);
    },
  },
  {
    id: "clogged-pipe",
    title: "Clogged pipe",
    story: "The pump motor runs, but no water comes out.",
    expect: "Ideally an alert that watering did not work",
    knownGap: "The system cannot see this yet: the pump ran but the soil stayed dry and no alert said so. A flow sensor or a \"soil did not rise after watering\" check would close the gap.",
    timeoutSec: 180,
    setup: (lab) => {
      const { s, a } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      a.noEffect = true;
      s.value = 33;
      lab.weather = { ...NORMAL_WEATHER, label: "Dry spell", evaporation: 1 };
    },
    check: (lab, o) => {
      const pump = lab.actuator("WATER_PUMP")!;
      if (!o.turnedOn.has(pump.id) || !o.turnedOffAfterOn.has(pump.id)) return "waiting";
      // The run is over: did anything tell the farmer that the water never arrived?
      const warned = o.alerts.some((a) => /water|flow|did not rise/i.test(a.message) && !a.message.includes("started"));
      return warned ? "pass" : "gap";
    },
  },
  {
    id: "sensor-offline",
    title: "Sensor cable cut",
    story: "A goat chews through the soil sensor's cable; it stops sending.",
    expect: "An \"is offline\" alert after about 2 minutes",
    timeoutSec: 200,
    setup: (lab) => {
      const { s } = needs(lab, "SOIL_MOISTURE");
      s.sending = false;
    },
    check: (lab, o) => (alertWith(o, `${lab.sensor("SOIL_MOISTURE")!.name} is offline`) ? "pass" : "waiting"),
  },
  {
    id: "impossible-value",
    title: "Faulty sensor reading",
    story: "Water gets into the sensor and it reports 150 % moisture.",
    expect: "The server rejects the value (HTTP 422) instead of acting on it",
    timeoutSec: 20,
    async setup(lab) {
      const { s } = needs(lab, "SOIL_MOISTURE");
      await lab.sendRaw(s.id, 150);
    },
    check: (_lab, o) => (o.rejected > 0 ? "pass" : "waiting"),
  },
  {
    id: "power-cut",
    title: "Power cut",
    story: "The farm loses power: every device goes silent.",
    expect: "An \"is offline\" alert for every sensor; relays stay off (fail-safe)",
    timeoutSec: 210,
    setup: (lab) => {
      for (const s of lab.sensors) s.sending = false;
    },
    check: (lab, o) => (lab.sensors.every((s) => alertWith(o, `${s.name} is offline`)) ? "pass" : "waiting"),
  },
];

/** Devices and rules the "Create lab farm" button makes, so every scenario has what it needs. */
export const LAB_FARM = {
  crop: { name: "Lab vegetable bed", cropName: "Lettuce" },
  poultry: { name: "Lab chicken house", birdCount: 50 },
  sensors: [
    { zone: "crop", type: "SOIL_MOISTURE", name: "Lab soil" },
    { zone: "poultry", type: "TEMPERATURE", name: "Lab house temperature" },
    { zone: "poultry", type: "FEED_LEVEL", name: "Lab feed tray" },
  ],
  actuators: [
    { zone: "crop", type: "WATER_PUMP", name: "Lab pump", maxRunMinutes: 2 },
    { zone: "poultry", type: "FAN", name: "Lab fan", maxRunMinutes: 2 },
    { zone: "poultry", type: "FEEDER", name: "Lab feeder", maxRunMinutes: 2 },
  ],
  // Short runs and a 1-minute cooldown so scenarios can follow each other quickly
  rules: [
    { name: "Lab dry soil", sensor: "Lab soil", condition: "BELOW", threshold: 30, actuator: "Lab pump", durationMin: 1, severity: "WARNING" },
    { name: "Lab hot house", sensor: "Lab house temperature", condition: "ABOVE", threshold: 32, actuator: "Lab fan", durationMin: 1, severity: "WARNING" },
    { name: "Lab low feed", sensor: "Lab feed tray", condition: "BELOW", threshold: 20, actuator: "Lab feeder", durationMin: 1, severity: "WARNING" },
  ],
} as const;

/** A signed-in user's API call: method, path, optional JSON body; returns the parsed answer. */
export type UserApi = (method: string, path: string, body?: unknown) => Promise<any>;

interface FarmZone {
  id: number;
  name: string;
  sensors: { id: number; name: string; type: LabSensorType; zoneId: number; simulated: boolean }[];
  actuators: { id: number; name: string; type: LabActuatorType; zoneId: number }[];
}

/**
 * Creates the lab zones, real (non-simulated) devices and short-cooldown rules if they are missing,
 * then adds them to the lab. Needs the Farm Owner (zones and rules). Safe to run again.
 */
export async function ensureLabFarm(api: UserApi, lab: FarmLab) {
  let farm: { zones: FarmZone[] } = await api("GET", "/api/farm");
  const zoneOf = (key: "crop" | "poultry") => farm.zones.find((z) => z.name === LAB_FARM[key].name);
  if (!zoneOf("crop")) await api("POST", "/api/zones", { type: "CROP", ...LAB_FARM.crop });
  if (!zoneOf("poultry")) await api("POST", "/api/zones", { type: "POULTRY", ...LAB_FARM.poultry });
  farm = await api("GET", "/api/farm");
  for (const s of LAB_FARM.sensors) {
    const zone = zoneOf(s.zone)!;
    if (!zone.sensors.some((x) => x.name === s.name)) {
      await api("POST", `/api/zones/${zone.id}/sensors`, { type: s.type, name: s.name, simulated: false });
    }
  }
  for (const a of LAB_FARM.actuators) {
    const zone = zoneOf(a.zone)!;
    if (!zone.actuators.some((x) => x.name === a.name)) {
      await api("POST", `/api/zones/${zone.id}/actuators`, { type: a.type, name: a.name, maxRunMinutes: a.maxRunMinutes });
    }
  }
  farm = await api("GET", "/api/farm");
  const devices = farm.zones.filter((z) => z.name === LAB_FARM.crop.name || z.name === LAB_FARM.poultry.name);
  const sensorId = (name: string) => devices.flatMap((z) => z.sensors).find((s) => s.name === name)!.id;
  const actuatorId = (name: string) => devices.flatMap((z) => z.actuators).find((a) => a.name === name)!.id;
  const rules: { name: string }[] = await api("GET", "/api/rules");
  for (const r of LAB_FARM.rules) {
    if (rules.some((x) => x.name === r.name)) continue;
    await api("POST", "/api/rules", {
      name: r.name, sensorId: sensorId(r.sensor), condition: r.condition, threshold: r.threshold,
      actuatorId: actuatorId(r.actuator), durationMin: r.durationMin, severity: r.severity, cooldownMinutes: 1,
    });
  }
  for (const z of devices) {
    for (const s of z.sensors) lab.addSensor({ id: s.id, name: s.name, type: s.type, zoneId: s.zoneId });
    for (const a of z.actuators) lab.addActuator({ id: a.id, name: a.name, type: a.type, zoneId: a.zoneId });
  }
  return devices;
}
