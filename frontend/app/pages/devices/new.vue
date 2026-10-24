<script setup lang="ts">
import { ArrowLeft, Check, Plus } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { Actuator, ActuatorType, Farm, Sensor, SensorType } from "~/utils/types";
import { actuatorHints, actuatorLabels, defaultRunMinutes, sensorHints, sensorLabels } from "~/utils/types";
import { actuatorIcons, sensorIcons } from "~/utils/deviceIcons";

// Full page to register a device: zone, kind, type (with what each one is for), name and settings.
// After saving it shows the #id the ESP32 needs and the next steps.

const api = useApi();
const route = useRoute();
const { can } = useAuth();
const farm = ref<Farm | null>(null);
const error = ref("");
const busy = ref(false);
const created = ref<{ kind: "sensor" | "actuator"; device: Sensor | Actuator; zoneName: string } | null>(null);

const form = reactive({
  zoneId: route.query.zone ? Number(route.query.zone) : 0,
  kind: (route.query.kind === "actuator" ? "actuator" : "sensor") as "sensor" | "actuator",
  sensorType: "SOIL_MOISTURE" as SensorType,
  actuatorType: "WATER_PUMP" as ActuatorType,
  name: "",
  simulated: true,
  maxRunMinutes: defaultRunMinutes.WATER_PUMP,
});

onMounted(async () => {
  try {
    farm.value = await api<Farm>("/api/farm");
    if (!farm.value.zones.some((z) => z.id === form.zoneId)) form.zoneId = farm.value.zones[0]?.id ?? 0;
  } catch (e) {
    error.value = errorMessage(e);
  }
});

watch(() => form.actuatorType, (t) => { form.maxRunMinutes = defaultRunMinutes[t]; });

const zone = computed(() => farm.value?.zones.find((z) => z.id === form.zoneId));
const types = computed(() => form.kind === "sensor"
  ? (Object.keys(sensorLabels) as SensorType[]).map((k) => ({ key: k, label: sensorLabels[k], hint: sensorHints[k], icon: sensorIcons[k] }))
  : (Object.keys(actuatorLabels) as ActuatorType[]).map((k) => ({ key: k, label: actuatorLabels[k], hint: actuatorHints[k], icon: actuatorIcons[k] })));
const chosen = computed(() => (form.kind === "sensor" ? form.sensorType : form.actuatorType));

function choose(key: string) {
  if (form.kind === "sensor") form.sensorType = key as SensorType;
  else form.actuatorType = key as ActuatorType;
}

/** "Heater 2" when the zone already has one heater: a name staff can tell apart. */
const suggestedName = computed(() => {
  const label = form.kind === "sensor" ? sensorLabels[form.sensorType] : actuatorLabels[form.actuatorType];
  const same = form.kind === "sensor"
    ? zone.value?.sensors.filter((s) => s.type === form.sensorType).length ?? 0
    : zone.value?.actuators.filter((a) => a.type === form.actuatorType).length ?? 0;
  return `${label} ${same + 1}`;
});

async function save() {
  if (busy.value) return;
  error.value = "";
  busy.value = true;
  const name = form.name.trim() || suggestedName.value;
  try {
    const device = form.kind === "sensor"
      ? await api<Sensor>(`/api/zones/${form.zoneId}/sensors`, { method: "POST", body: { type: form.sensorType, name, simulated: form.simulated } })
      : await api<Actuator>(`/api/zones/${form.zoneId}/actuators`, { method: "POST", body: { type: form.actuatorType, name, maxRunMinutes: form.maxRunMinutes } });
    created.value = { kind: form.kind, device, zoneName: zone.value?.name ?? "" };
    farm.value = await api<Farm>("/api/farm");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}

function another() {
  created.value = null;
  form.name = "";
}

/** The line a technician pastes into the ESP32's secrets.h for a real sensor or actuator. */
const firmwareLine = computed(() => {
  const c = created.value;
  if (!c) return "";
  if (c.kind === "actuator") return c.device.type === "WATER_PUMP" ? `#define PUMP_ACTUATOR_ID ${c.device.id}` : "";
  const sensor = c.device as Sensor;
  if (sensor.simulated) return "";
  const define = { SOIL_MOISTURE: "SOIL_SENSOR_ID", TEMPERATURE: "TEMP_SENSOR_ID" }[sensor.type as string];
  return define ? `#define ${define} ${sensor.id}` : "";
});
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <NuxtLink to="/devices" class="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft class="size-4" /> All devices
    </NuxtLink>
    <PageHeader title="Add device" description="Register a sensor or an actuator in a zone. A real ESP32 then uses the #id shown after saving." />

    <Card v-if="!can('TECHNICIAN')">
      <CardContent class="py-6 text-sm text-muted-foreground">Only a Technician or the Farm Owner can register devices.</CardContent>
    </Card>

    <!-- Saved: what to do next -->
    <Card v-else-if="created">
      <CardHeader>
        <CardTitle class="flex items-center gap-2"><Check class="size-5 text-primary" /> {{ created.device.name }} is registered</CardTitle>
        <CardDescription>
          {{ created.kind === "sensor" ? `${sensorLabels[created.device.type as SensorType]} sensor` : actuatorLabels[created.device.type as ActuatorType] }}
          in {{ created.zoneName }} · device <b>#{{ created.device.id }}</b>
        </CardDescription>
      </CardHeader>
      <CardContent class="flex flex-col gap-4">
        <div v-if="firmwareLine" class="flex flex-col gap-1.5">
          <span class="text-sm">For a real ESP32, put this line in <code>secrets.h</code>:</span>
          <code class="rounded-md bg-muted px-3 py-2 font-mono text-sm">{{ firmwareLine }}</code>
        </div>
        <p v-else-if="created.kind === 'sensor' && (created.device as Sensor).simulated" class="text-sm text-muted-foreground">
          It is simulated: the built-in simulator starts sending readings for it within a few seconds.
        </p>
        <p v-else class="text-sm text-muted-foreground">Devices and the Farm Lab use the #id above to talk to the server.</p>
        <p class="text-sm text-muted-foreground">
          Next: a rule makes it react by itself, for example
          {{ created.kind === "actuator" ? "start it when a sensor in this zone crosses a value" : "start an actuator when this reading crosses a value" }}.
        </p>
        <div class="flex flex-wrap gap-2">
          <Button @click="another"><Plus /> Add another</Button>
          <Button variant="outline" as-child><NuxtLink :to="{ path: '/rules', query: { zone: String(created.device.zoneId) } }">Set up a rule</NuxtLink></Button>
          <Button variant="ghost" as-child><NuxtLink to="/devices">All devices</NuxtLink></Button>
        </div>
      </CardContent>
    </Card>

    <form v-else class="flex flex-col gap-4" @submit.prevent="save">
      <Card>
        <CardHeader>
          <CardTitle>1. Where</CardTitle>
          <CardDescription>A device belongs to one zone; rules only connect devices of the same zone.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-1.5">
          <Label for="nzone">Zone</Label>
          <NativeSelect id="nzone" v-model.number="form.zoneId" class="w-full sm:w-80" required>
            <NativeSelectOption v-for="z in farm?.zones ?? []" :key="z.id" :value="z.id">{{ z.name }} ({{ z.type === "CROP" ? "crop" : "poultry" }})</NativeSelectOption>
          </NativeSelect>
          <p v-if="farm && !farm.zones.length" class="text-sm text-destructive">No zones yet: the Farm Owner creates one on the Zones page first.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>2. What</CardTitle>
          <CardDescription>A sensor measures; an actuator does something (pump, fan, heater...).</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-4">
          <div class="inline-flex w-fit rounded-lg border p-1" role="radiogroup" aria-label="Kind">
            <button v-for="k in (['sensor', 'actuator'] as const)" :key="k" type="button" role="radio" :aria-checked="form.kind === k"
              class="rounded-md px-4 py-1.5 text-sm font-medium transition-colors"
              :class="form.kind === k ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
              @click="form.kind = k">{{ k === "sensor" ? "Sensor" : "Actuator" }}</button>
          </div>
          <div class="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label="Type">
            <button v-for="t in types" :key="t.key" type="button" role="radio" :aria-checked="chosen === t.key"
              class="flex items-start gap-3 rounded-lg border p-3 text-left transition-colors hover:border-primary/50"
              :class="chosen === t.key ? 'border-primary bg-primary/5 ring-1 ring-primary' : ''"
              @click="choose(t.key)">
              <component :is="t.icon" class="mt-0.5 size-5 shrink-0" :class="chosen === t.key ? 'text-primary' : 'text-muted-foreground'" />
              <span>
                <span class="block text-sm font-medium">{{ t.label }}</span>
                <span class="block text-xs text-muted-foreground">{{ t.hint }}</span>
              </span>
            </button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>3. Details</CardTitle>
        </CardHeader>
        <CardContent class="flex flex-col gap-4">
          <div class="flex flex-col gap-1.5">
            <Label for="nname">Name</Label>
            <Input id="nname" v-model="form.name" maxlength="80" :placeholder="suggestedName" />
            <p class="text-xs text-muted-foreground">Leave empty to use "{{ suggestedName }}".</p>
          </div>
          <label v-if="form.kind === 'sensor'" class="flex items-start gap-2 text-sm">
            <input v-model="form.simulated" type="checkbox" class="mt-0.5 size-4 accent-[var(--primary)]">
            <span>Simulated<span class="block text-xs text-muted-foreground">Untick for a real ESP32 sensor or a Farm Lab device, so the built-in simulator leaves it alone.</span></span>
          </label>
          <div v-else class="flex flex-col gap-1.5">
            <Label for="nmax">Safety limit (longest run, minutes)</Label>
            <Input id="nmax" v-model.number="form.maxRunMinutes" type="number" min="1" max="240" required class="w-32" />
            <p class="text-xs text-muted-foreground">No rule, schedule or person can run it longer than this in one go.</p>
          </div>
          <FormError :message="error" />
          <div class="flex gap-2">
            <Button type="submit" :disabled="busy || !form.zoneId">Register device</Button>
            <Button type="button" variant="ghost" as-child><NuxtLink to="/devices">Cancel</NuxtLink></Button>
          </div>
        </CardContent>
      </Card>
    </form>
  </div>
</template>
