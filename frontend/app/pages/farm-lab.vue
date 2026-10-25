<script setup lang="ts">
import { Bird, CloudRain, FlaskConical, Flower2, Play, Plus, Power, PowerOff, Sprout, Square, Trash2, Unplug, Wand2, Zap } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { Alert as FarmAlert, Farm, Sensor, SensorAction } from "~/utils/types";
import type { LabIO, LabSensorType, LabActuatorType, Scenario, Weather } from "~/utils/farmLab";
import { FarmLab, LAB_UI, NORMAL_WEATHER, SCENARIOS, ensureLabFarm } from "~/utils/farmLab";
import type { LabSensor } from "~/utils/farmLab";
import { sensorBadge, sensorNote, sensorOptions } from "~/utils/sensorRepair";


definePageMeta({ role: "TECHNICIAN" });
// Many virtual devices that behave like a small farm and talk to the server like real ESP32 boards.
// Scenarios change the weather or break a device and check how the system reacts.

const KEY_STORE = "smartfarm.deviceKey";
const DEVICES_STORE = "smartfarm.labDevices";

const api = useApi();
const { can, token } = useAuth();
const farm = ref<Farm | null>(null);
const error = ref("");
const deviceKey = ref("");
const log = ref<{ at: string; text: string; tone: string }[]>([]);
const busy = ref(false);
const scenarioRunning = ref<string | null>(null);

// Lab-zone devices talk through the signed-in account (no device key needed); a real farm device added
// to the lab still needs the device key, like a real ESP32.
const labZoneIds = computed(() => new Set((farm.value?.zones ?? []).filter((z) => z.lab).map((z) => z.id)));
const inLabZone = (zoneId: number | undefined) => zoneId !== undefined && labZoneIds.value.has(zoneId);
const deviceHeaders = (lab: boolean): Record<string, string> =>
  lab ? { Authorization: `Bearer ${token.value ?? ""}` } : { "X-Device-Key": deviceKey.value };

const io: LabIO = {
  async sendReading(sensorId, value) {
    const lab = inLabZone(allSensors.value.find((s) => s.id === sensorId)?.zoneId);
    try {
      const res = await $fetch.raw(lab ? "/api/lab/readings" : "/api/devices/readings", {
        method: "POST", body: { sensorId, value }, headers: deviceHeaders(lab), ignoreResponseError: true,
      });
      return res.status;
    } catch {
      return -1;
    }
  },
  async relayState(actuatorId) {
    const lab = inLabZone(allActuators.value.find((a) => a.id === actuatorId)?.zoneId);
    try {
      const res = await $fetch.raw<{ relayOn: boolean }>(lab ? `/api/lab/actuators/${actuatorId}/state` : `/api/devices/actuators/${actuatorId}/state`, {
        headers: deviceHeaders(lab), ignoreResponseError: true,
      });
      return res.status === 200 ? !!res._data?.relayOn : null;
    } catch {
      return null;
    }
  },
  // the lab's own alerts, plus those of any real farm device added to the lab
  openAlerts: () => api<FarmAlert[]>("/api/alerts", { query: { status: "OPEN", scope: "ALL" } }),
  async lifecycle(actuatorId, action, body) {
    await api(`/api/actuators/${actuatorId}/lifecycle/${action}`, { method: "POST", body: body ?? {} });
  },
  async sensorLifecycle(sensorId, action) {
    const sensor = await api<Sensor>(`/api/sensors/${sensorId}/lifecycle/${action}`, { method: "POST" });
    return sensor.status;
  },
  sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
  log(text, tone = "info") {
    log.value.unshift({ at: new Date().toLocaleTimeString(), text, tone });
    log.value.length = Math.min(log.value.length, 120);
  },
};

const lab = reactive(new FarmLab(io)) as unknown as FarmLab;

const WEATHER: Weather[] = [
  { ...NORMAL_WEATHER },
  { ...NORMAL_WEATHER, label: "Dry spell", evaporation: 2 },
  { ...NORMAL_WEATHER, label: "Heavy rain", evaporation: 0, rain: 3 },
  { ...NORMAL_WEATHER, label: "Heat wave", ambient: 39 },
  { ...NORMAL_WEATHER, label: "Cool night", ambient: 21 },
  { ...NORMAL_WEATHER, label: "Hungry flock", feedUse: 2 },
  { ...NORMAL_WEATHER, label: "Cold snap", ambient: 10 },
  { ...NORMAL_WEATHER, label: "Dry air", humidity: 20 },
  { ...NORMAL_WEATHER, label: "Dark day", light: 1500 },
  { ...NORMAL_WEATHER, label: "Leaky tank", tankUse: 2 },
];

const allSensors = computed(() => (farm.value?.zones ?? []).flatMap((z) => z.sensors.map((s) => ({ ...s, zoneName: z.name }))));
const allActuators = computed(() => (farm.value?.zones ?? []).flatMap((z) =>
  z.actuators.filter((a) => a.status !== "DECOMMISSIONED").map((a) => ({ ...a, zoneName: z.name }))));
const zoneName = (id: number) => farm.value?.zones.find((z) => z.id === id)?.name ?? "";
const addChoice = ref("");
const addable = computed(() => [
  ...allSensors.value.filter((s) => !lab.sensors.some((x) => x.id === s.id)).map((s) => ({ key: `s${s.id}`, label: `Sensor: ${s.zoneName} — ${s.name} #${s.id}${s.simulated ? " (simulated)" : ""}` })),
  ...allActuators.value.filter((a) => !lab.actuators.some((x) => x.id === a.id)).map((a) => ({ key: `a${a.id}`, label: `Actuator: ${a.zoneName} — ${a.name} #${a.id}` })),
]);
/** What the server says about a lab sensor (its status changes when a technician repairs it). */
const serverSensor = (id: number) => allSensors.value.find((x) => x.id === id);

async function repairStep(id: number, action: SensorAction) {
  error.value = "";
  try {
    const status = await io.sensorLifecycle(id, action);
    const failed = action === "COMPLETE_REPAIR" && status !== "ONLINE";
    io.log(`Sensor #${id}: ${action.toLowerCase().replace("_", " ")} -> ${status}${failed ? " (test failed: no valid reading since the repair began)" : ""}`, failed ? "warn" : "ok");
    await loadFarm();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

const simulatedInLab = computed(() => lab.sensors.some((s) => allSensors.value.find((x) => x.id === s.id)?.simulated));

function remember() {
  try {
    localStorage.setItem(DEVICES_STORE, JSON.stringify({ s: lab.sensors.map((s) => s.id), a: lab.actuators.map((a) => a.id) }));
  } catch {
    // ignore blocked storage
  }
}

function addById(key: string) {
  const id = Number(key.slice(1));
  if (key.startsWith("s")) {
    const s = allSensors.value.find((x) => x.id === id);
    if (s) lab.addSensor({ id: s.id, name: s.name, type: s.type, zoneId: s.zoneId, value: s.latest?.value,
      range: { unit: s.unit, min: s.minValid, max: s.maxValid, typical: s.typical } });
  } else {
    const a = allActuators.value.find((x) => x.id === id);
    if (a) lab.addActuator({ id: a.id, name: a.name, type: a.type, zoneId: a.zoneId, effects: a.effects });
  }
}

function addChosen() {
  if (!addChoice.value) return;
  addById(addChoice.value);
  addChoice.value = "";
  remember();
}

function addAllReal() {
  for (const s of allSensors.value.filter((x) => !x.simulated)) addById(`s${s.id}`);
  const labZones = new Set(lab.sensors.map((s) => s.zoneId));
  for (const a of allActuators.value.filter((x) => labZones.has(x.zoneId))) addById(`a${a.id}`);
  remember();
}

function removeDevice(kind: "sensor" | "actuator", id: number) {
  lab.remove(kind, id);
  remember();
}

async function loadFarm() {
  farm.value = await api<Farm>("/api/farm");
}

onMounted(async () => {
  try {
    deviceKey.value = sessionStorage.getItem(KEY_STORE) ?? "";
  } catch {
    deviceKey.value = "";
  }
  try {
    await loadFarm();
    const saved = JSON.parse(localStorage.getItem(DEVICES_STORE) ?? "{}") as { s?: number[]; a?: number[] };
    (saved.s ?? []).forEach((id) => addById(`s${id}`));
    (saved.a ?? []).forEach((id) => addById(`a${id}`));
  } catch (e) {
    error.value = errorMessage(e);
  }
});
onBeforeUnmount(() => lab.stop());
usePolling(async () => {
  try {
    await loadFarm();
  } catch {
    // the actions on this page report connection problems
  }
}, 4000);

watch(deviceKey, (k) => {
  try {
    sessionStorage.setItem(KEY_STORE, k);
  } catch {
    // ignore blocked storage
  }
});

async function createLabFarm() {
  busy.value = true;
  error.value = "";
  try {
    await ensureLabFarm(async (method, path, body) => api(path, { method: method as "GET", body: body as Record<string, unknown> }), lab);
    await loadFarm();
    remember();
    io.log("Lab farm ready: 3 zones, 6 sensors, pump, fan, feeder, heater, mister, lamp and tank valve, with rules (1-minute cooldown)", "ok");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}

/** Only real farm devices added to the lab need the device key; lab-zone devices use the sign-in. */
const needsKey = computed(() => [...lab.sensors, ...lab.actuators].some((d) => !inLabZone(d.zoneId)));

function canStart() {
  if (needsKey.value && !deviceKey.value.trim()) {
    error.value = "A real farm device is in the lab: enter the device key (FARM_DEVICE_KEY on the server), or remove it.";
    return false;
  }
  if (!lab.sensors.length && !lab.actuators.length) {
    error.value = "Add devices first, or create the lab farm.";
    return false;
  }
  error.value = "";
  return true;
}

function powerOn() {
  if (!canStart()) return;
  io.log(`Devices on: ${lab.sensors.length} sensor(s) send every 5 s, ${lab.actuators.length} relay(s) checked every 5 s`, "ok");
  lab.run();
}

function powerOff() {
  lab.stop();
  io.log("Devices off", "warn");
}

async function runScenario(s: Scenario) {
  if (!canStart() || scenarioRunning.value) return;
  if (!lab.running) lab.run();
  scenarioRunning.value = s.id;
  try {
    await lab.runScenario(s, false);
  } catch (e) {
    io.log(`${s.title}: ${(e as Error).message}`, "error");
  } finally {
    scenarioRunning.value = null;
  }
}

async function runAll() {
  if (!canStart() || scenarioRunning.value) return;
  if (!lab.running) lab.run();
  scenarioRunning.value = "all";
  try {
    await lab.runAll(SCENARIOS, false);
  } catch (e) {
    io.log((e as Error).message, "error");
  } finally {
    scenarioRunning.value = null;
  }
}

const verdictOf = (id: string) => lab.results.find((r) => r.id === id);
const verdictStyle = { pass: "bg-primary/15 text-primary", fail: "bg-destructive/15 text-destructive", gap: "bg-warning/25 text-foreground" };
const verdictLabel = { pass: "PASS", fail: "FAIL", gap: "KNOWN GAP" };
/** The lab's devices grouped by zone, drawn as the physical places they are in. */
const labZones = computed(() => {
  const zones = farm.value?.zones ?? [];
  const ids = [...new Set([...lab.sensors, ...lab.actuators].map((d) => d.zoneId))];
  return ids.map((id) => {
    const z = zones.find((x) => x.id === id);
    return {
      id, name: z?.name ?? `Zone #${id}`, type: z?.type ?? "CROP", lab: !!z?.lab, detail: z?.detail ?? "",
      sensors: lab.sensors.filter((s) => s.zoneId === id), actuators: lab.actuators.filter((a) => a.zoneId === id),
    };
  });
});

function zoneLook(z: { type: string; name: string }) {
  if (z.type === "POULTRY") return { icon: Bird, label: "Chicken house", panel: "border-t-4 border-t-poultry bg-poultry/5", badge: "bg-poultry/15 text-poultry" };
  if (/greenhouse/i.test(z.name)) return { icon: Flower2, label: "Greenhouse", panel: "border-t-4 border-t-greenhouse bg-greenhouse/5", badge: "bg-greenhouse/15 text-greenhouse" };
  return { icon: Sprout, label: "Crop bed", panel: "border-t-4 border-t-crop bg-crop/5", badge: "bg-crop/15 text-crop" };
}

const sensorState = (id: number) => serverSensor(id)?.status ?? "ONLINE";

/** Unit, slider and impossible value for any lab sensor; catalog measurements use their own range. */
function labUi(s: LabSensor) {
  const known = LAB_UI[s.type as LabSensorType];
  if (known) return known;
  const min = s.range?.min ?? 0;
  const max = s.range?.max ?? 100;
  return { unit: s.range?.unit ?? "", min, max, step: (max - min) / 200, impossible: max + (max - min) + 1 };
}

const lightColor: Record<LabActuatorType, string> = {
  WATER_PUMP: "bg-sky-500 shadow-[0_0_24px_6px_rgba(14,165,233,0.55)]",
  FAN: "bg-teal-400 shadow-[0_0_24px_6px_rgba(45,212,191,0.55)]",
  FEEDER: "bg-amber-400 shadow-[0_0_24px_6px_rgba(251,191,36,0.55)]",
  HEATER: "bg-orange-500 shadow-[0_0_24px_6px_rgba(249,115,22,0.55)]",
  MISTER: "bg-cyan-300 shadow-[0_0_24px_6px_rgba(103,232,249,0.55)]",
  LIGHT: "bg-yellow-300 shadow-[0_0_24px_6px_rgba(253,224,71,0.6)]",
  WATER_VALVE: "bg-indigo-400 shadow-[0_0_24px_6px_rgba(129,140,248,0.55)]",
};
</script>

<template>
  <div>
    <PageHeader title="Farm Lab"
      description="Virtual sensors and actuators that behave like a small farm and talk to the server exactly like real ESP32 boards. Change the weather, break a device, or run a scenario to see how the system reacts.">
      <Button v-if="can()" variant="outline" :disabled="busy || lab.running" @click="createLabFarm"><Wand2 /> Create lab farm</Button>
      <Button v-if="!lab.running" @click="powerOn"><Power /> Power on</Button>
      <Button v-else variant="outline" :disabled="!!scenarioRunning" @click="powerOff"><PowerOff /> Power off</Button>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="simulatedInLab" class="mb-4">
      <AlertDescription>A lab sensor is "simulated", so the built-in simulator also sends values for it. Use sensors registered with Simulated unticked for clean results.</AlertDescription>
    </Alert>

    <div class="grid-cols-1 mb-4 grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      <Card>
        <CardHeader>
          <CardTitle>Connection and weather</CardTitle>
          <CardDescription>Lab zones use your sign-in. The device key is only for real farm devices added to the lab, and stays in this browser tab.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <Label for="lkey">Device key <span class="font-normal text-muted-foreground">{{ needsKey ? "(needed: a real farm device is in the lab)" : "(not needed for lab zones)" }}</span></Label>
            <Input id="lkey" v-model="deviceKey" type="password" autocomplete="off" placeholder="FARM_DEVICE_KEY from the server" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label>Weather now: <b>{{ lab.weather.label }}</b></Label>
            <div class="flex flex-wrap gap-1.5">
              <Button v-for="w in WEATHER" :key="w.label" size="sm" :variant="lab.weather.label === w.label ? 'default' : 'outline'"
                :disabled="!!scenarioRunning" @click="lab.weather = { ...w }">
                <CloudRain v-if="w.label === 'Heavy rain'" /> {{ w.label }}
              </Button>
            </div>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="ladd">Add a device from the farm</Label>
            <div class="flex gap-2">
              <NativeSelect id="ladd" v-model="addChoice" class="w-full min-w-0">
                <NativeSelectOption value="">Choose…</NativeSelectOption>
                <NativeSelectOption v-for="d in addable" :key="d.key" :value="d.key">{{ d.label }}</NativeSelectOption>
              </NativeSelect>
              <Button variant="outline" :disabled="!addChoice" @click="addChosen"><Plus /> Add</Button>
            </div>
            <Button variant="ghost" size="sm" class="self-start" @click="addAllReal">Add every real (non-simulated) device</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2"><FlaskConical class="size-4" /> Scenarios</CardTitle>
          <CardDescription>Each one changes the farm and checks the server's reaction. "Run all" takes about 30 minutes.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-2">
          <div class="max-h-[420px] overflow-auto pr-1">
            <div v-for="s in SCENARIOS" :key="s.id" class="flex items-start gap-3 border-b py-2 last:border-b-0">
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2 text-sm font-medium">
                  {{ s.title }}
                  <span v-if="verdictOf(s.id)" class="rounded px-1.5 py-0.5 text-[11px] font-semibold" :class="verdictStyle[verdictOf(s.id)!.verdict]">
                    {{ verdictLabel[verdictOf(s.id)!.verdict] }} · {{ verdictOf(s.id)!.seconds }} s
                  </span>
                  <span v-else-if="lab.current?.scenario.id === s.id" class="text-xs text-primary">running {{ Math.round(lab.current.observed.elapsedSec) }} s…</span>
                </div>
                <div class="text-xs text-muted-foreground">{{ s.story }} <b>Expect:</b> {{ s.expect }}</div>
                <div v-if="verdictOf(s.id)?.verdict === 'gap'" class="mt-1 text-xs">{{ verdictOf(s.id)!.note }}</div>
              </div>
              <Button size="sm" variant="outline" :disabled="!!scenarioRunning" :aria-label="`Run ${s.title}`" @click="runScenario(s)"><Play /></Button>
            </div>
          </div>
          <Button :disabled="!!scenarioRunning" @click="runAll"><Play /> Run all scenarios</Button>
        </CardContent>
      </Card>
    </div>

    <div v-if="labZones.length" class="mb-4 flex flex-col gap-4">
      <section v-for="z in labZones" :key="z.id" class="rounded-2xl border p-4" :class="zoneLook(z).panel">
        <header class="mb-3 flex flex-wrap items-center gap-3">
          <span class="flex size-10 items-center justify-center rounded-xl" :class="zoneLook(z).badge"><component :is="zoneLook(z).icon" class="size-5" /></span>
          <div class="min-w-0">
            <h2 class="font-semibold">{{ z.name }}</h2>
            <p class="text-xs text-muted-foreground">{{ zoneLook(z).label }}{{ z.detail ? ` · ${z.detail}` : "" }} · {{ z.sensors.length }} sensor(s), {{ z.actuators.length }} actuator(s)</p>
          </div>
          <span class="ml-auto rounded-full px-2.5 py-1 text-xs font-medium"
            :class="z.lab ? 'bg-primary/15 text-primary' : 'bg-warning/25 text-foreground'">{{ z.lab ? "Lab zone · alerts stay here" : "Real farm zone · needs the device key" }}</span>
        </header>

        <div class="grid-cols-1 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
          <!-- Sensors -->
          <article v-for="s in z.sensors" :key="`s${s.id}`" class="flex flex-col gap-2 rounded-xl border bg-card p-3 shadow-sm"
            :class="{ 'border-destructive/60': sensorState(s.id) === 'FAULT', 'border-warning': sensorState(s.id) === 'MAINTENANCE' }">
            <div class="stage rounded-md p-2">
              <DeviceArt :type="s.type" :value="s.value" :offline="!s.sending || sensorState(s.id) === 'OFFLINE'"
                :fault="sensorState(s.id) === 'FAULT' || sensorState(s.id) === 'MAINTENANCE'" class="mx-auto max-w-[220px]" />
            </div>
            <div class="flex items-start gap-2">
              <div class="min-w-0 flex-1">
                <div class="truncate font-medium">{{ s.name }}</div>
                <div class="text-xs text-muted-foreground">Sensor #{{ s.id }} · {{ s.sending ? "plugged in" : "unplugged" }}</div>
              </div>
              <Button size="icon-sm" variant="ghost" :aria-label="`Remove ${s.name} from the lab`" @click="removeDevice('sensor', s.id)"><Trash2 /></Button>
            </div>
            <div class="flex items-baseline gap-2">
              <span class="text-2xl font-semibold tabular-nums" :class="{ 'text-muted-foreground line-through': !s.sending }">{{ s.value.toFixed(s.type === 'LIGHT_LEVEL' ? 0 : 1) }}</span>
              <span class="text-sm text-muted-foreground">{{ labUi(s).unit }}</span>
              <span v-if="s.lastStatus !== null" class="ml-auto text-xs"
                :class="s.lastStatus === 201 ? 'text-primary' : s.lastStatus === 202 ? 'text-amber-600' : 'text-destructive'">HTTP {{ s.lastStatus }}</span>
            </div>
            <input v-model.number="s.value" type="range" :min="labUi(s).min" :max="labUi(s).max" :step="labUi(s).step"
              class="accent-[var(--primary)]" :aria-label="`${s.name} value`">
            <div v-if="serverSensor(s.id)" class="flex flex-col gap-1.5">
              <div class="flex items-center gap-2 text-xs text-muted-foreground">
                <span>Server sees it as</span><StatusBadge :status="sensorBadge(serverSensor(s.id)!)" />
              </div>
              <p v-if="sensorNote(serverSensor(s.id)!)" class="text-xs text-muted-foreground">{{ sensorNote(serverSensor(s.id)!) }}</p>
              <div v-if="can('TECHNICIAN')" class="flex flex-wrap gap-1.5">
                <Button v-for="o in sensorOptions[serverSensor(s.id)!.status]" :key="o.label" size="xs" variant="outline"
                  @click="repairStep(s.id, o.action)">{{ o.label }}</Button>
              </div>
            </div>
            <div class="mt-auto flex flex-wrap gap-1.5 border-t pt-2">
              <Button size="xs" :variant="s.sending ? 'outline' : 'destructive'" @click="s.sending = !s.sending">
                <Unplug /> {{ s.sending ? "Unplug" : "Plug back in" }}
              </Button>
              <Button size="xs" variant="outline" @click="lab.sendRaw(s.id, labUi(s).impossible)">
                <Zap /> Send impossible value
              </Button>
            </div>
          </article>

          <!-- Actuators -->
          <article v-for="a in z.actuators" :key="`a${a.id}`" class="flex flex-col gap-2 rounded-xl border bg-card p-3 shadow-sm"
            :class="{ 'ring-2 ring-primary/40': a.relayOn }">
            <div class="stage relative rounded-md p-2">
              <DeviceArt :type="a.type" kind="actuator" :on="a.relayOn" :broken="a.noEffect" class="mx-auto max-w-[220px]" />
              <span class="absolute top-2 left-2 size-3 rounded-full transition-all" :class="a.relayOn ? (lightColor[a.type as LabActuatorType] ?? 'bg-emerald-400') : 'bg-muted-foreground/30'"
                role="img" :aria-label="`${a.name} relay ${a.relayOn ? 'on' : 'off'}`" />
            </div>
            <div class="flex items-start gap-2">
              <div class="min-w-0 flex-1">
                <div class="truncate font-medium">{{ a.name }}</div>
                <div class="text-xs text-muted-foreground">Actuator #{{ a.id }}</div>
              </div>
              <Button size="icon-sm" variant="ghost" :aria-label="`Remove ${a.name} from the lab`" @click="removeDevice('actuator', a.id)"><Trash2 /></Button>
            </div>
            <div class="flex items-center gap-2">
              <span class="rounded-full px-2.5 py-0.5 text-sm font-semibold" :class="a.relayOn ? 'bg-primary/15 text-primary' : 'bg-muted text-muted-foreground'">
                Relay {{ a.relayOn ? "ON" : "OFF" }}
              </span>
              <span v-if="a.noEffect" class="text-xs font-medium text-destructive">does nothing</span>
            </div>
            <label class="mt-auto flex items-center gap-2 border-t pt-2 text-xs">
              <input v-model="a.noEffect" type="checkbox" class="size-3.5 accent-[var(--primary)]">
              Runs but does nothing (clogged pipe, burnt motor)
            </label>
          </article>
        </div>
      </section>
    </div>
    <Card v-else class="mb-4">
      <CardContent class="py-6 text-center text-sm text-muted-foreground">
        No devices in the lab yet. {{ can() ? 'Click "Create lab farm" for a ready-made set, or add' : "Add" }} devices from the farm above.
      </CardContent>
    </Card>

    <Card>
      <CardHeader class="flex-row items-center">
        <div>
          <CardTitle>Lab log</CardTitle>
          <CardDescription>What the devices sent and how the server answered.</CardDescription>
        </div>
        <Button size="sm" variant="ghost" class="ml-auto" :disabled="!log.length" @click="log = []"><Square /> Clear</Button>
      </CardHeader>
      <CardContent>
        <div class="h-64 overflow-auto rounded-md bg-zinc-950 p-3 font-mono text-xs leading-relaxed" role="log" aria-live="polite">
          <p v-if="!log.length" class="text-zinc-500">Power on the devices or run a scenario.</p>
          <p v-for="(l, i) in log" :key="i" :class="{ 'text-emerald-300': l.tone === 'ok', 'text-amber-300': l.tone === 'warn', 'text-red-400': l.tone === 'error', 'text-sky-300': l.tone === 'info' }">
            <span class="text-zinc-500">{{ l.at }}</span> {{ l.text }}
          </p>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
