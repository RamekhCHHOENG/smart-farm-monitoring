// Farm Lab: any number of virtual sensors and actuators that behave like a small farm, plus
// scenarios (drought, heat wave, broken pump, power cut...) that check how the server reacts.
// Devices talk to the server through the same device API and device key as a real ESP32.
// Shared by the Farm Lab page and scripts/farm-scenarios.mjs, so it has no imports.

export type LabSensorType = "SOIL_MOISTURE" | "TEMPERATURE" | "FEED_LEVEL" | "HUMIDITY" | "LIGHT_LEVEL" | "WATER_LEVEL";
export type LabActuatorType = "WATER_PUMP" | "FAN" | "FEEDER" | "HEATER" | "MISTER" | "LIGHT" | "WATER_VALVE";

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
  humidity: number; // air humidity % the air drifts towards
  light: number; // lux the daylight drifts towards
  tankUse: number; // tank % lost per tick (a leak, or animals drinking)
}

export const NORMAL_WEATHER: Weather = {
  label: "Normal day", evaporation: 0.3, rain: 0, ambient: 28, feedUse: 0.3, humidity: 65, light: 15_000, tankUse: 0.1,
};

/** Healthy starting values, between the default rule thresholds. */
export const HEALTHY: Record<LabSensorType, number> = {
  SOIL_MOISTURE: 55, TEMPERATURE: 28, FEED_LEVEL: 60, HUMIDITY: 65, LIGHT_LEVEL: 15_000, WATER_LEVEL: 70,
};

/** What the server accepts as a valid reading, per kind of sensor. */
const RANGE: Record<LabSensorType, [number, number]> = {
  SOIL_MOISTURE: [0, 100],
  TEMPERATURE: [-10, 60],
  FEED_LEVEL: [0, 100],
  HUMIDITY: [0, 100],
  LIGHT_LEVEL: [0, 100_000],
  WATER_LEVEL: [0, 100],
};

/** How the page draws a lab sensor: unit, slider and a value the server must reject. */
export const LAB_UI: Record<LabSensorType, { unit: string; min: number; max: number; step: number; impossible: number }> = {
  SOIL_MOISTURE: { unit: "%", min: 0, max: 100, step: 0.5, impossible: 150 },
  TEMPERATURE: { unit: "°C", min: 0, max: 50, step: 0.5, impossible: 99 },
  FEED_LEVEL: { unit: "%", min: 0, max: 100, step: 0.5, impossible: 150 },
  HUMIDITY: { unit: "%", min: 0, max: 100, step: 0.5, impossible: 150 },
  LIGHT_LEVEL: { unit: "lux", min: 0, max: 50_000, step: 100, impossible: 500_000 },
  WATER_LEVEL: { unit: "%", min: 0, max: 100, step: 0.5, impossible: 150 },
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
  /** Technician lifecycle action on an actuator, e.g. FAULT, START_REPAIR, COMPLETE_REPAIR */
  lifecycle(actuatorId: number, action: string, body?: Record<string, unknown>): Promise<void>;
  /** Technician repair step on a sensor: FAULT, START_REPAIR or COMPLETE_REPAIR; resolves to the sensor's new status */
  sensorLifecycle(sensorId: number, action: string): Promise<string>;
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
  check(lab: FarmLab, o: Observed): Verdict | Promise<Verdict>;
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
        s.value += (w.ambient - s.value) * 0.15 - (working("FAN", s.zoneId) ? 1.2 : 0)
          + (working("HEATER", s.zoneId) ? 1.5 : 0) + noise;
      } else if (s.type === "HUMIDITY") {
        s.value += (w.humidity - s.value) * 0.1 + (working("MISTER", s.zoneId) ? 4 : 0)
          - (working("FAN", s.zoneId) ? 1 : 0) + noise;
      } else if (s.type === "LIGHT_LEVEL") {
        s.value += (w.light - s.value) * 0.2 + (working("LIGHT", s.zoneId) ? 3000 : 0) + noise * 1000;
      } else if (s.type === "WATER_LEVEL") {
        s.value += -w.tankUse + (working("WATER_VALVE", s.zoneId) ? 5 : 0)
          - (working("WATER_PUMP", s.zoneId) ? 0.5 : 0) + noise / 2;
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
      if (status === 202) this.io.log(`${s.name} = ${v} -> ignored by the server (sensor is marked faulty)`, "warn");
      else if (status !== 201) this.io.log(`${s.name} = ${v} -> HTTP ${status}`, status === 422 ? "warn" : "error");
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
        verdict = await scenario.check(this, observed);
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

/** Progress of the "Sensor repaired" scenario: 0 faulty, 1 in repair, 2 back in service. */
const repair = { step: 0 };

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
    id: "cold-snap",
    title: "Cold snap",
    story: "A cold front drops the chicken house towards 10 °C at night.",
    expect: "The heater starts below 18 °C",
    timeoutSec: 150,
    setup: (lab) => {
      needs(lab, "TEMPERATURE", "HEATER");
      lab.weather = { ...NORMAL_WEATHER, label: "Cold snap", ambient: 10 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("HEATER")!.id) ? "pass" : "waiting"),
  },
  {
    id: "thermostat",
    title: "Thermostat",
    story: "The chicken house is at 35 °C on a normal day. The fan cools it; then the weather does the rest.",
    expect: "The fan stops by itself at 30 °C, long before its 2-minute limit, and the heater never starts",
    timeoutSec: 120,
    setup: (lab) => {
      const { s } = needs(lab, "TEMPERATURE", "FAN");
      needs(lab, "TEMPERATURE", "HEATER");
      s.value = 35;
      lab.weather = { ...NORMAL_WEATHER, label: "Normal day" };
    },
    check: (lab, o) => {
      const fan = lab.actuator("FAN")!;
      if (o.turnedOn.has(lab.actuator("HEATER")!.id)) return "fail"; // fan and heater took turns
      if (!o.turnedOffAfterOn.has(fan.id)) return "waiting";
      // Stopped well before the 2-minute safety limit: the rule's target stopped it, not the clock
      return o.elapsedSec < 90 ? "pass" : "fail";
    },
  },
  {
    id: "dry-air",
    title: "Dry air",
    story: "A dry wind pulls the humidity of the greenhouse down to 20 %.",
    expect: "The mister starts below 40 % humidity",
    timeoutSec: 150,
    setup: (lab) => {
      const { s } = needs(lab, "HUMIDITY", "MISTER");
      s.value = 44;
      lab.weather = { ...NORMAL_WEATHER, label: "Dry air", humidity: 20 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("MISTER")!.id) ? "pass" : "waiting"),
  },
  {
    id: "dark-day",
    title: "Dark day",
    story: "Thick storm clouds darken the greenhouse.",
    expect: "The light switches on below 5000 lux",
    timeoutSec: 150,
    setup: (lab) => {
      const { s } = needs(lab, "LIGHT_LEVEL", "LIGHT");
      s.value = 9000;
      lab.weather = { ...NORMAL_WEATHER, label: "Dark day", light: 1500 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("LIGHT")!.id) ? "pass" : "waiting"),
  },
  {
    id: "tank-low",
    title: "Tank running low",
    story: "A leak drains the water tank faster than usual.",
    expect: "The tank valve opens below 20 % so the pumps never run dry",
    timeoutSec: 150,
    setup: (lab) => {
      const { s } = needs(lab, "WATER_LEVEL", "WATER_VALVE");
      s.value = 27;
      lab.weather = { ...NORMAL_WEATHER, label: "Leaky tank", tankUse: 2 };
    },
    check: (lab, o) => (o.turnedOn.has(lab.actuator("WATER_VALVE")!.id) ? "pass" : "waiting"),
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
    id: "sensor-repair",
    title: "Sensor repaired",
    story: "The soil sensor is reported faulty while the bed dries out. A technician starts the repair, then tests the sensor and puts it back.",
    expect: "While faulty or in repair the pump stays off; once the sensor is back in service the dry reading starts the pump",
    timeoutSec: 150,
    setup(lab) {
      const { s } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      repair.step = 0;
      s.value = 36;
      lab.weather = { ...NORMAL_WEATHER, label: "Dry spell", evaporation: 2 };
      return lab.io.sensorLifecycle(s.id, "FAULT").then(() => undefined);
    },
    async check(lab, o) {
      const { s, a } = needs(lab, "SOIL_MOISTURE", "WATER_PUMP");
      const pumpRan = o.turnedOn.has(a.id);
      if (pumpRan && repair.step < 2) return "fail"; // a broken or half-repaired sensor started the pump
      if (repair.step === 0 && o.elapsedSec >= 30) {
        await lab.io.sensorLifecycle(s.id, "START_REPAIR");
        repair.step = 1;
      } else if (repair.step === 1 && o.elapsedSec >= 45) {
        // Several valid readings arrived during the repair (the pump still waits); now the test
        const status = await lab.io.sensorLifecycle(s.id, "COMPLETE_REPAIR");
        if (status !== "ONLINE") return "fail";
        repair.step = 2;
      }
      return pumpRan && repair.step === 2 ? "pass" : "waiting";
    },
    async cleanup(lab) {
      const s = lab.sensor("SOIL_MOISTURE");
      if (!s || repair.step === 2) return;
      // Leave the sensor in service for the next scenario
      try {
        if (repair.step === 0) await lab.io.sensorLifecycle(s.id, "START_REPAIR");
        await lab.io.sendReading(s.id, Math.round(s.value * 10) / 10);
        await lab.io.sensorLifecycle(s.id, "COMPLETE_REPAIR");
      } catch {
        // already in service
      }
    },
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
  greenhouse: { name: "Lab greenhouse", cropName: "Tomato" },
  sensors: [
    { zone: "crop", type: "SOIL_MOISTURE", name: "Lab soil" },
    { zone: "poultry", type: "TEMPERATURE", name: "Lab house temperature" },
    { zone: "poultry", type: "FEED_LEVEL", name: "Lab feed tray" },
    { zone: "greenhouse", type: "HUMIDITY", name: "Lab humidity" },
    { zone: "greenhouse", type: "LIGHT_LEVEL", name: "Lab light level" },
    { zone: "greenhouse", type: "WATER_LEVEL", name: "Lab tank" },
  ],
  actuators: [
    { zone: "crop", type: "WATER_PUMP", name: "Lab pump", maxRunMinutes: 2 },
    { zone: "poultry", type: "FAN", name: "Lab fan", maxRunMinutes: 2 },
    { zone: "poultry", type: "FEEDER", name: "Lab feeder", maxRunMinutes: 2 },
    { zone: "poultry", type: "HEATER", name: "Lab heater", maxRunMinutes: 2 },
    { zone: "greenhouse", type: "MISTER", name: "Lab mister", maxRunMinutes: 2 },
    { zone: "greenhouse", type: "LIGHT", name: "Lab lamp", maxRunMinutes: 2 },
    { zone: "greenhouse", type: "WATER_VALVE", name: "Lab tank valve", maxRunMinutes: 2 },
  ],
  // Short runs and a 1-minute cooldown so scenarios can follow each other quickly
  rules: [
    { name: "Lab dry soil", sensor: "Lab soil", condition: "BELOW", threshold: 30, actuator: "Lab pump", durationMin: 1, severity: "WARNING" },
    // Fan and heater keep the house between 21 and 30 °C: each stops at its target, long before the other one starts
    { name: "Lab hot house", sensor: "Lab house temperature", condition: "ABOVE", threshold: 32, actuator: "Lab fan", durationMin: 2, severity: "WARNING", stopAt: 30 },
    { name: "Lab low feed", sensor: "Lab feed tray", condition: "BELOW", threshold: 20, actuator: "Lab feeder", durationMin: 1, severity: "WARNING" },
    { name: "Lab cold house", sensor: "Lab house temperature", condition: "BELOW", threshold: 18, actuator: "Lab heater", durationMin: 2, severity: "WARNING", stopAt: 21 },
    { name: "Lab dry air", sensor: "Lab humidity", condition: "BELOW", threshold: 40, actuator: "Lab mister", durationMin: 1, severity: "WARNING" },
    { name: "Lab dark", sensor: "Lab light level", condition: "BELOW", threshold: 5000, actuator: "Lab lamp", durationMin: 1, severity: "WARNING" },
    { name: "Lab low tank", sensor: "Lab tank", condition: "BELOW", threshold: 20, actuator: "Lab tank valve", durationMin: 1, severity: "WARNING" },
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
  const zoneOf = (key: "crop" | "poultry" | "greenhouse") => farm.zones.find((z) => z.name === LAB_FARM[key].name);
  if (!zoneOf("crop")) await api("POST", "/api/zones", { type: "CROP", ...LAB_FARM.crop });
  if (!zoneOf("poultry")) await api("POST", "/api/zones", { type: "POULTRY", ...LAB_FARM.poultry });
  if (!zoneOf("greenhouse")) await api("POST", "/api/zones", { type: "CROP", ...LAB_FARM.greenhouse });
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
  const labZones: string[] = [LAB_FARM.crop.name, LAB_FARM.poultry.name, LAB_FARM.greenhouse.name];
  const devices = farm.zones.filter((z) => labZones.includes(z.name));
  const sensorId = (name: string) => devices.flatMap((z) => z.sensors).find((s) => s.name === name)!.id;
  const actuatorId = (name: string) => devices.flatMap((z) => z.actuators).find((a) => a.name === name)!.id;
  const rules: { id: number; name: string; stopAt: number | null; durationMin: number }[] = await api("GET", "/api/rules");
  for (const r of LAB_FARM.rules) {
    const stopAt = "stopAt" in r ? r.stopAt : null;
    const body = {
      name: r.name, sensorId: sensorId(r.sensor), condition: r.condition, threshold: r.threshold,
      actuatorId: actuatorId(r.actuator), durationMin: r.durationMin, severity: r.severity, cooldownMinutes: 1, stopAt,
    };
    const existing = rules.find((x) => x.name === r.name);
    if (!existing) await api("POST", "/api/rules", body);
    // A lab farm made by an older version: bring its rule up to date (for example "keep in range")
    else if (existing.stopAt !== stopAt || existing.durationMin !== r.durationMin) await api("PUT", `/api/rules/${existing.id}`, body);
  }
  for (const z of devices) {
    for (const s of z.sensors) lab.addSensor({ id: s.id, name: s.name, type: s.type, zoneId: s.zoneId });
    for (const a of z.actuators) lab.addActuator({ id: a.id, name: a.name, type: a.type, zoneId: a.zoneId });
  }
  return devices;
}
