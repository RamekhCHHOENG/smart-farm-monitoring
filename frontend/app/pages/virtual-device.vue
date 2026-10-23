<script setup lang="ts">
import { Cpu, Power, PowerOff, Send, Trash2 } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { Actuator, Farm, Sensor } from "~/utils/types";
import { sensorLabels } from "~/utils/types";

// A stand-in for the ESP32 node: it uses the same device API and device key as the firmware,
// so the server cannot tell it from a real board. Useful when there is no hardware yet.

const KEY_STORE = "smartfarm.deviceKey";
const READING_INTERVAL_MS = 10000; // same as the firmware
const COMMAND_INTERVAL_MS = 3000;

const api = useApi();
const farm = ref<Farm | null>(null);
const error = ref("");

const deviceKey = ref("");
const soilId = ref(0);
const tempId = ref(0);
const pumpId = ref(0);
const soilValue = ref(45);
const tempValue = ref(29);
const drift = ref(true);
const running = ref(false);
const relayOn = ref(false);
const log = ref<{ at: string; text: string; tone: "ok" | "warn" | "error" }[]>([]);

const sensors = computed(() => (farm.value?.zones ?? []).flatMap((z) => z.sensors.map((s) => ({ ...s, zoneName: z.name }))));
const pumps = computed(() => (farm.value?.zones ?? []).flatMap((z) =>
  z.actuators.filter((a) => a.status !== "DECOMMISSIONED").map((a) => ({ ...a, zoneName: z.name }))));
const soil = computed(() => sensors.value.find((s) => s.id === soilId.value));
const temp = computed(() => sensors.value.find((s) => s.id === tempId.value));
const pump = computed(() => pumps.value.find((a) => a.id === pumpId.value));
const simulatedChosen = computed(() => [soil.value, temp.value].some((s) => s?.simulated));

onMounted(async () => {
  try {
    deviceKey.value = sessionStorage.getItem(KEY_STORE) ?? "";
  } catch {
    deviceKey.value = "";
  }
  try {
    farm.value = await api<Farm>("/api/farm");
    // Prefer devices registered for real hardware ("Simulated" unticked)
    const real = sensors.value.filter((s) => !s.simulated);
    soilId.value = (real.find((s) => s.type !== "TEMPERATURE") ?? sensors.value.find((s) => s.type !== "TEMPERATURE"))?.id ?? 0;
    pumpId.value = pumps.value.find((a) => a.zoneId === soil.value?.zoneId)?.id ?? pumps.value[0]?.id ?? 0;
  } catch (e) {
    error.value = errorMessage(e);
  }
});

watch(deviceKey, (k) => {
  try {
    sessionStorage.setItem(KEY_STORE, k);
  } catch {
    // ignore blocked storage
  }
});
watch(soil, (s) => {
  if (s) soilValue.value = s.latest?.value ?? (s.minValid + s.maxValid) / 2;
});
watch(temp, (s) => {
  if (s) tempValue.value = s.latest?.value ?? 29;
});

function print(text: string, tone: "ok" | "warn" | "error" = "ok") {
  log.value.unshift({ at: new Date().toLocaleTimeString(), text, tone });
  log.value.length = Math.min(log.value.length, 60);
}

async function device<T>(path: string, body?: unknown): Promise<{ status: number; data: T | null }> {
  try {
    const res = await $fetch.raw<T>(path, {
      method: body ? "POST" : "GET",
      body: body as Record<string, unknown> | undefined,
      headers: { "X-Device-Key": deviceKey.value },
      ignoreResponseError: true,
    });
    return { status: res.status, data: (res._data ?? null) as T | null };
  } catch {
    return { status: -1, data: null }; // server not reachable
  }
}

type ReadingResult = { status: string; actions: string[]; error: string | null; detail?: string };

async function sendReading(sensor: Sensor | undefined, value: number) {
  if (!sensor) return;
  const v = Math.round(value * 10) / 10;
  const { status, data } = await device<ReadingResult>("/api/devices/readings", { sensorId: sensor.id, value: v });
  const detail = data?.actions?.length ? data.actions.join("; ") : data?.error ?? data?.detail ?? data?.status ?? "";
  print(`Sensor ${sensor.id} = ${v} -> HTTP ${status} ${detail}`, status === 201 ? "ok" : status === 422 ? "warn" : "error");
}

async function sendAll() {
  if (drift.value && soil.value) {
    // Dry soil loses a little water every reading; a running pump wets it quickly
    const next = soilValue.value + (relayOn.value ? 6 : -1.5);
    soilValue.value = Math.min(soil.value.maxValid, Math.max(soil.value.minValid, next));
  }
  await sendReading(soil.value, soilValue.value);
  if (temp.value) await sendReading(temp.value, tempValue.value);
}

async function pollPump() {
  if (!pump.value) return;
  const { status, data } = await device<{ relayOn: boolean; status: string }>(`/api/devices/actuators/${pump.value.id}/state`);
  const on = status === 200 && !!data?.relayOn;
  if (on !== relayOn.value) print(`Pump ${pump.value.id} -> ${on ? "ON" : "OFF"}${status === 200 ? ` (${data?.status})` : ` (HTTP ${status}, fail-safe off)`}`, status === 200 ? "ok" : "error");
  relayOn.value = on; // no answer from the server means pump off, like the firmware
}

let readingTimer: ReturnType<typeof setInterval> | undefined;
let commandTimer: ReturnType<typeof setInterval> | undefined;

function start() {
  if (!deviceKey.value.trim()) {
    error.value = "Enter the device key first (FARM_DEVICE_KEY on the server).";
    return;
  }
  if (!soil.value && !temp.value) {
    error.value = "Choose at least one sensor.";
    return;
  }
  error.value = "";
  running.value = true;
  print("Device powered on: readings every 10 s, pump check every 3 s");
  sendAll();
  pollPump();
  readingTimer = setInterval(sendAll, READING_INTERVAL_MS);
  commandTimer = setInterval(pollPump, COMMAND_INTERVAL_MS);
}

function stop() {
  clearInterval(readingTimer);
  clearInterval(commandTimer);
  running.value = false;
  relayOn.value = false;
  print("Device powered off", "warn");
}
onBeforeUnmount(stop);

function label(s: Sensor & { zoneName: string }) {
  return `${s.zoneName} — ${s.name} #${s.id} (${sensorLabels[s.type]}${s.simulated ? ", simulated" : ""})`;
}
function pumpLabel(a: Actuator & { zoneName: string }) {
  return `${a.zoneName} — ${a.name} #${a.id}`;
}
</script>

<template>
  <div>
    <PageHeader title="Virtual ESP32"
      description="A device in the browser for testing without hardware. It uses the same device API and device key as the real ESP32, so rules, alerts and the pump react exactly as they would in the field.">
      <Button v-if="!running" @click="start"><Power /> Power on</Button>
      <Button v-else variant="outline" @click="stop"><PowerOff /> Power off</Button>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="simulatedChosen" class="mb-4">
      <AlertDescription>
        A chosen sensor is "simulated", so the built-in simulator also sends values for it. For a clean test,
        register a sensor with Simulated unticked (Zones &amp; devices) and choose it here.
      </AlertDescription>
    </Alert>

    <div class="grid gap-4 lg:grid-cols-[1fr_1fr]">
      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2"><Cpu class="size-4" /> Board</CardTitle>
          <CardDescription>Wire it like secrets.h: the sensor and pump ids, and the device key.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <Label for="vkey">Device key</Label>
            <Input id="vkey" v-model="deviceKey" type="password" autocomplete="off" :disabled="running" placeholder="FARM_DEVICE_KEY from the server" />
            <p class="text-xs text-muted-foreground">Kept only in this browser tab, never saved on the server or in the app.</p>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="vsoil">Soil (or feed level) sensor</Label>
            <NativeSelect id="vsoil" v-model.number="soilId" class="w-full" :disabled="running">
              <NativeSelectOption :value="0">None</NativeSelectOption>
              <NativeSelectOption v-for="s in sensors.filter((x) => x.type !== 'TEMPERATURE')" :key="s.id" :value="s.id">{{ label(s) }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div v-if="soil" class="flex flex-col gap-1.5">
            <Label for="vsoilv">Value: <span class="tabular-nums">{{ soilValue.toFixed(1) }} {{ soil.unit }}</span></Label>
            <input id="vsoilv" v-model.number="soilValue" type="range" :min="soil.minValid" :max="soil.maxValid" step="0.5" class="accent-[var(--primary)]">
            <label class="flex items-center gap-2 text-sm">
              <input v-model="drift" type="checkbox" class="size-4 accent-[var(--primary)]">
              Dries by itself; a running pump wets it again
            </label>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="vtemp">Temperature sensor (optional)</Label>
            <NativeSelect id="vtemp" v-model.number="tempId" class="w-full" :disabled="running">
              <NativeSelectOption :value="0">None</NativeSelectOption>
              <NativeSelectOption v-for="s in sensors.filter((x) => x.type === 'TEMPERATURE')" :key="s.id" :value="s.id">{{ label(s) }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div v-if="temp" class="flex flex-col gap-1.5">
            <Label for="vtempv">Temperature: <span class="tabular-nums">{{ tempValue.toFixed(1) }} {{ temp.unit }}</span></Label>
            <input id="vtempv" v-model.number="tempValue" type="range" :min="temp.minValid" :max="temp.maxValid" step="0.5" class="accent-[var(--primary)]">
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="vpump">Pump on the relay</Label>
            <NativeSelect id="vpump" v-model.number="pumpId" class="w-full" :disabled="running">
              <NativeSelectOption :value="0">None</NativeSelectOption>
              <NativeSelectOption v-for="a in pumps" :key="a.id" :value="a.id">{{ pumpLabel(a) }}</NativeSelectOption>
            </NativeSelect>
          </div>
        </CardContent>
      </Card>

      <div class="flex flex-col gap-4">
        <Card>
          <CardContent class="flex items-center gap-5">
            <div class="flex size-16 shrink-0 items-center justify-center rounded-full border-4 transition-all duration-300"
              :class="relayOn ? 'border-sky-300 bg-sky-500 shadow-[0_0_32px_8px_rgba(14,165,233,0.55)]' : 'border-muted bg-muted'"
              role="img" :aria-label="relayOn ? 'Pump LED on' : 'Pump LED off'" />
            <div class="min-w-0 flex-1">
              <div class="text-lg font-semibold">Pump relay {{ relayOn ? "ON" : "OFF" }}</div>
              <div class="text-sm text-muted-foreground">
                {{ running ? (pump ? `Asks the server every 3 s whether ${pump.name} should run` : "No pump chosen") : "Device is off" }}
              </div>
            </div>
            <Button size="sm" variant="outline" :disabled="!running" @click="sendAll"><Send /> Send now</Button>
          </CardContent>
        </Card>

        <Card class="flex-1">
          <CardHeader>
            <CardTitle>Serial monitor</CardTitle>
            <CardDescription>What the board would print over USB.</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="h-80 overflow-auto rounded-md bg-zinc-950 p-3 font-mono text-xs leading-relaxed" role="log" aria-live="polite">
              <p v-if="!log.length" class="text-zinc-500">Power on the device to start.</p>
              <p v-for="(l, i) in log" :key="i" :class="{ 'text-emerald-300': l.tone === 'ok', 'text-amber-300': l.tone === 'warn', 'text-red-400': l.tone === 'error' }">
                <span class="text-zinc-500">{{ l.at }}</span> {{ l.text }}
              </p>
            </div>
            <Button size="sm" variant="ghost" class="mt-2" :disabled="!log.length" @click="log = []"><Trash2 /> Clear</Button>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
