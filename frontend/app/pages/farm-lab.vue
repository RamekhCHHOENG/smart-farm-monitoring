<script setup lang="ts">
import { CloudRain, FlaskConical, Play, Plus, Power, PowerOff, Square, Trash2, Unplug, Wand2, Zap } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { Alert as FarmAlert, Farm } from "~/utils/types";
import type { LabIO, LabSensorType, LabActuatorType, Scenario, Weather } from "~/utils/farmLab";
import { FarmLab, NORMAL_WEATHER, SCENARIOS, ensureLabFarm } from "~/utils/farmLab";

// Many virtual devices that behave like a small farm and talk to the server like real ESP32 boards.
// Scenarios change the weather or break a device and check how the system reacts.

const KEY_STORE = "smartfarm.deviceKey";
const DEVICES_STORE = "smartfarm.labDevices";

const api = useApi();
const { can } = useAuth();
const farm = ref<Farm | null>(null);
const error = ref("");
const deviceKey = ref("");
const log = ref<{ at: string; text: string; tone: string }[]>([]);
const busy = ref(false);
const scenarioRunning = ref<string | null>(null);

const io: LabIO = {
  async sendReading(sensorId, value) {
    try {
      const res = await $fetch.raw("/api/devices/readings", {
        method: "POST", body: { sensorId, value }, headers: { "X-Device-Key": deviceKey.value }, ignoreResponseError: true,
      });
      return res.status;
    } catch {
      return -1;
    }
  },
  async relayState(actuatorId) {
    try {
      const res = await $fetch.raw<{ relayOn: boolean }>(`/api/devices/actuators/${actuatorId}/state`, {
        headers: { "X-Device-Key": deviceKey.value }, ignoreResponseError: true,
      });
      return res.status === 200 ? !!res._data?.relayOn : null;
    } catch {
      return null;
    }
  },
  openAlerts: () => api<FarmAlert[]>("/api/alerts", { query: { status: "OPEN" } }),
  async lifecycle(actuatorId, action, body) {
    await api(`/api/actuators/${actuatorId}/lifecycle/${action}`, { method: "POST", body: body ?? {} });
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
    if (s) lab.addSensor({ id: s.id, name: s.name, type: s.type as LabSensorType, zoneId: s.zoneId, value: s.latest?.value });
  } else {
    const a = allActuators.value.find((x) => x.id === id);
    if (a) lab.addActuator({ id: a.id, name: a.name, type: a.type as LabActuatorType, zoneId: a.zoneId });
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
    io.log("Lab farm ready: 2 zones, 3 sensors, pump, fan and feeder, with rules (1-minute cooldown)", "ok");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}

function canStart() {
  if (!deviceKey.value.trim()) {
    error.value = "Enter the device key first (FARM_DEVICE_KEY on the server).";
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
const unitOf = (t: LabSensorType) => (t === "TEMPERATURE" ? "°C" : "%");
const rangeOf = (t: LabSensorType) => (t === "TEMPERATURE" ? [0, 50] : [0, 100]);
const lightColor: Record<LabActuatorType, string> = {
  WATER_PUMP: "bg-sky-500 shadow-[0_0_24px_6px_rgba(14,165,233,0.55)]",
  FAN: "bg-teal-400 shadow-[0_0_24px_6px_rgba(45,212,191,0.55)]",
  FEEDER: "bg-amber-400 shadow-[0_0_24px_6px_rgba(251,191,36,0.55)]",
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

    <div class="mb-4 grid gap-4 lg:grid-cols-[1fr_1.4fr]">
      <Card>
        <CardHeader>
          <CardTitle>Connection and weather</CardTitle>
          <CardDescription>The device key is kept only in this browser tab.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <Label for="lkey">Device key</Label>
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
          <CardDescription>Each one changes the farm and checks the server's reaction. "Run all" takes about 20 minutes.</CardDescription>
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

    <div class="mb-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <Card v-for="s in lab.sensors" :key="`s${s.id}`" class="gap-2 py-4">
        <CardContent class="flex flex-col gap-2 px-4">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ s.name }}</span>
            <span class="text-xs text-muted-foreground">#{{ s.id }} · {{ zoneName(s.zoneId) }}</span>
            <Button size="icon-sm" variant="ghost" class="ml-auto" :aria-label="`Remove ${s.name} from the lab`" @click="removeDevice('sensor', s.id)"><Trash2 /></Button>
          </div>
          <div class="flex items-baseline gap-2">
            <span class="text-2xl font-semibold tabular-nums" :class="{ 'text-muted-foreground line-through': !s.sending }">{{ s.value.toFixed(1) }}</span>
            <span class="text-sm text-muted-foreground">{{ unitOf(s.type) }}</span>
            <span v-if="s.lastStatus !== null" class="ml-auto text-xs" :class="s.lastStatus === 201 ? 'text-primary' : 'text-destructive'">HTTP {{ s.lastStatus }}</span>
          </div>
          <input v-model.number="s.value" type="range" :min="rangeOf(s.type)[0]" :max="rangeOf(s.type)[1]" step="0.5"
            class="accent-[var(--primary)]" :aria-label="`${s.name} value`">
          <div class="flex flex-wrap gap-1.5">
            <Button size="xs" :variant="s.sending ? 'outline' : 'destructive'" @click="s.sending = !s.sending">
              <Unplug /> {{ s.sending ? "Unplug" : "Plug back in" }}
            </Button>
            <Button size="xs" variant="outline" @click="lab.sendRaw(s.id, s.type === 'TEMPERATURE' ? 99 : 150)">
              <Zap /> Send impossible value
            </Button>
          </div>
        </CardContent>
      </Card>
      <Card v-for="a in lab.actuators" :key="`a${a.id}`" class="gap-2 py-4">
        <CardContent class="flex items-center gap-4 px-4">
          <div class="size-10 shrink-0 rounded-full transition-all duration-300" :class="a.relayOn ? lightColor[a.type] : 'bg-muted'"
            role="img" :aria-label="`${a.name} relay ${a.relayOn ? 'on' : 'off'}`" />
          <div class="min-w-0 flex-1">
            <div class="font-medium">{{ a.name }} <span class="text-xs font-normal text-muted-foreground">#{{ a.id }} · {{ zoneName(a.zoneId) }}</span></div>
            <div class="text-sm text-muted-foreground">Relay {{ a.relayOn ? "ON" : "OFF" }}{{ a.noEffect ? " · no effect" : "" }}</div>
            <label class="mt-1 flex items-center gap-2 text-xs">
              <input v-model="a.noEffect" type="checkbox" class="size-3.5 accent-[var(--primary)]">
              Runs but does nothing (clogged pipe, burnt motor)
            </label>
          </div>
          <Button size="icon-sm" variant="ghost" :aria-label="`Remove ${a.name} from the lab`" @click="removeDevice('actuator', a.id)"><Trash2 /></Button>
        </CardContent>
      </Card>
      <Card v-if="!lab.sensors.length && !lab.actuators.length" class="sm:col-span-2 xl:col-span-3">
        <CardContent class="py-6 text-center text-sm text-muted-foreground">
          No devices in the lab yet. {{ can() ? 'Click "Create lab farm" for a ready-made set, or add' : "Add" }} devices from the farm above.
        </CardContent>
      </Card>
    </div>

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
