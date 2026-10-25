<script setup lang="ts">
import { ArrowLeft, Check, Plus, Search } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { ActuatorType, ControllerBoard, DeviceModel, Farm, FarmDevice, MeasurementType } from "~/utils/types";
import { actuatorLabels } from "~/utils/types";


definePageMeta({ role: "TECHNICIAN" });
// Install a device on the farm, chosen from the catalog (master data): zone, model (with its picture and what
// it measures or does), then name, safety limit and controller board. Shows the #ids the ESP32 needs afterwards.

const api = useApi();
const route = useRoute();
const { can } = useAuth();
const farm = ref<Farm | null>(null);
const models = ref<DeviceModel[]>([]);
const measurements = ref<MeasurementType[]>([]);
const boards = ref<ControllerBoard[]>([]);
const error = ref("");
const busy = ref(false);
const created = ref<FarmDevice | null>(null);

const form = reactive({
  zoneId: route.query.zone ? Number(route.query.zone) : 0,
  modelId: route.query.model ? Number(route.query.model) : 0,
  kind: (route.query.kind === "actuator" ? "ACTUATOR" : "SENSOR") as "SENSOR" | "ACTUATOR",
  q: "",
  name: "",
  simulated: true,
  maxRunMinutes: 10,
  controllerId: "" as number | "",
  serialNumber: "",
  notes: "",
});

onMounted(async () => {
  try {
    [farm.value, models.value, measurements.value, boards.value] = await Promise.all([
      api<Farm>("/api/farm"), api<DeviceModel[]>("/api/catalog/models"),
      api<MeasurementType[]>("/api/catalog/measurements"), api<ControllerBoard[]>("/api/controllers"),
    ]);
    if (!farm.value.zones.some((z) => z.id === form.zoneId)) form.zoneId = farm.value.zones[0]?.id ?? 0;
    const preset = models.value.find((m) => m.id === form.modelId);
    if (preset && preset.category !== "CONTROLLER") form.kind = preset.category;
  } catch (e) {
    error.value = errorMessage(e);
  }
});

const zone = computed(() => farm.value?.zones.find((z) => z.id === form.zoneId));
const model = computed(() => models.value.find((m) => m.id === form.modelId));
const measureText = (code: string) => {
  const m = measurements.value.find((x) => x.code === code);
  return m ? `${m.name} (${m.unit})` : code;
};
const choices = computed(() => {
  const q = form.q.trim().toLowerCase();
  return models.value.filter((m) => m.category === form.kind && !m.retired
    && (!q || [m.name, m.manufacturer ?? "", ...m.channels.map((c) => measureText(c.measurementCode))].some((x) => x.toLowerCase().includes(q))));
});

watch(model, (m) => {
  if (m?.defaultMaxRunMinutes) form.maxRunMinutes = m.defaultMaxRunMinutes;
});
watch(() => form.kind, () => {
  if (model.value && model.value.category !== form.kind) form.modelId = 0;
});

/** "DHT22 2" when the zone already has one device of that model. */
const suggestedName = computed(() => {
  if (!model.value) return "";
  const short = model.value.name.split(/ (?:temperature|soil|waterproof|light|sensor|probe|kit|module|water|air)/i)[0] || model.value.name;
  const same = zone.value?.devices.filter((d) => d.modelId === model.value!.id).length ?? 0;
  return `${short} ${same + 1}`;
});

async function save() {
  if (busy.value || !model.value) return;
  error.value = "";
  busy.value = true;
  try {
    created.value = await api<FarmDevice>(`/api/zones/${form.zoneId}/devices`, {
      method: "POST",
      body: {
        modelId: form.modelId, name: form.name.trim() || suggestedName.value, simulated: form.simulated,
        maxRunMinutes: model.value.category === "ACTUATOR" ? form.maxRunMinutes : null,
        controllerId: form.controllerId === "" ? null : form.controllerId,
        serialNumber: form.serialNumber, notes: form.notes,
      },
    });
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
  form.serialNumber = "";
  form.notes = "";
}

const createdChannels = computed(() => {
  if (!created.value || !farm.value) return [];
  const z = farm.value.zones.find((x) => x.id === created.value!.zoneId);
  return [
    ...(z?.sensors.filter((s) => created.value!.sensorIds.includes(s.id)).map((s) => ({ id: s.id, text: `sensor #${s.id} · ${s.name} (${s.unit})` })) ?? []),
    ...(z?.actuators.filter((a) => created.value!.actuatorIds.includes(a.id)).map((a) => ({ id: a.id, text: `actuator #${a.id} · ${a.name}` })) ?? []),
  ];
});
</script>

<template>
  <div class="mx-auto max-w-5xl">
    <NuxtLink to="/devices" class="mb-3 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft class="size-4" /> All devices
    </NuxtLink>
    <PageHeader title="Add device" description="Choose a real device from the catalog and place it in a zone. Missing one? Add it to the catalog first." />

    <Card v-if="!can('TECHNICIAN')">
      <CardContent class="py-6 text-sm text-muted-foreground">Only a Technician or the Farm Owner can install devices.</CardContent>
    </Card>

    <!-- Saved: what to do next -->
    <Card v-else-if="created">
      <CardHeader>
        <CardTitle class="flex items-center gap-2"><Check class="size-5 text-primary" /> {{ created.name }} is installed</CardTitle>
        <CardDescription>{{ created.modelName }} in {{ zone?.name }}<template v-if="created.controllerName"> · on board {{ created.controllerName }}</template></CardDescription>
      </CardHeader>
      <CardContent class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center gap-4">
          <div class="w-48 rounded-lg bg-muted/40 p-2">
            <DevicePicture :model-id="created.modelId" :has-picture="created.hasPicture" :wokwi-part="created.wokwiPart" :art-type="created.artType"
              :kind="created.category === 'SENSOR' ? 'sensor' : 'actuator'" :height="110" />
          </div>
          <div class="flex flex-col gap-1">
            <span class="text-sm font-medium">The ids its readings and outputs use:</span>
            <code v-for="c in createdChannels" :key="c.id" class="rounded bg-muted px-2 py-1 font-mono text-sm">{{ c.text }}</code>
          </div>
        </div>
        <p class="text-sm text-muted-foreground">
          <template v-if="created.controllerId">The board's firmware settings list these ids: see <NuxtLink to="/controllers" class="underline">Controllers</NuxtLink>.</template>
          <template v-else>Put the ids in the ESP32's secrets.h, or assign the device to a controller board for generated settings.</template>
          Next, a rule makes it react by itself.
        </p>
        <div class="flex flex-wrap gap-2">
          <Button @click="another"><Plus /> Add another</Button>
          <Button variant="outline" as-child><NuxtLink :to="{ path: '/rules', query: { zone: String(created.zoneId) } }">Set up a rule</NuxtLink></Button>
          <Button variant="ghost" as-child><NuxtLink to="/devices">All devices</NuxtLink></Button>
        </div>
      </CardContent>
    </Card>

    <form v-else class="flex flex-col gap-4" @submit.prevent="save">
      <Card>
        <CardHeader>
          <CardTitle>1. Where</CardTitle>
          <CardDescription>A device belongs to one zone; rules connect devices of the same zone.</CardDescription>
        </CardHeader>
        <CardContent>
          <NativeSelect id="nzone" v-model.number="form.zoneId" class="w-full sm:w-80" aria-label="Zone" required>
            <NativeSelectOption v-for="z in farm?.zones ?? []" :key="z.id" :value="z.id">{{ z.name }}{{ z.lab ? " (lab)" : "" }}</NativeSelectOption>
          </NativeSelect>
          <p v-if="farm && !farm.zones.length" class="mt-2 text-sm text-destructive">No zones yet: the Farm Owner creates one on the Zones page first.</p>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>2. Which device</CardTitle>
          <CardDescription>From the catalog: {{ choices.length }} {{ form.kind === "SENSOR" ? "sensors" : "actuators" }}<template v-if="form.q"> match</template>.</CardDescription>
        </CardHeader>
        <CardContent class="flex flex-col gap-3">
          <div class="flex flex-wrap items-center gap-3">
            <div class="inline-flex rounded-lg border p-1" role="radiogroup" aria-label="Kind">
              <button v-for="k in (['SENSOR', 'ACTUATOR'] as const)" :key="k" type="button" role="radio" :aria-checked="form.kind === k"
                class="rounded-md px-4 py-1.5 text-sm font-medium"
                :class="form.kind === k ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
                @click="form.kind = k">{{ k === "SENSOR" ? "Sensor" : "Actuator" }}</button>
            </div>
            <div class="relative min-w-[14rem] flex-1">
              <Search class="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input v-model="form.q" type="search" class="pl-8" placeholder="DHT22, CO₂, pump, valve…" aria-label="Search the catalog" />
            </div>
            <NuxtLink to="/catalog" class="text-sm text-muted-foreground underline">Open the catalog</NuxtLink>
          </div>
          <div class="grid-cols-1 grid max-h-[520px] gap-2 overflow-y-auto pr-1 sm:grid-cols-2 lg:grid-cols-3" role="radiogroup" aria-label="Device model">
            <button v-for="m in choices" :key="m.id" type="button" role="radio" :aria-checked="form.modelId === m.id"
              class="flex flex-col gap-1.5 rounded-lg border p-2 text-left transition-colors hover:border-primary/50"
              :class="form.modelId === m.id ? 'border-primary bg-primary/5 ring-1 ring-primary' : ''" @click="form.modelId = m.id">
              <div class="rounded-md bg-muted/40">
                <DevicePicture :model-id="m.id" :has-picture="m.hasPicture" :wokwi-part="m.wokwiPart" :art-type="m.artType"
                  :type="m.channels[0]?.measurementCode ?? m.actuatorType ?? ''" :kind="m.category === 'SENSOR' ? 'sensor' : 'actuator'" :height="96" />
              </div>
              <span class="text-sm font-medium leading-tight">{{ m.name }}</span>
              <span class="text-xs text-muted-foreground">
                <template v-if="m.category === 'SENSOR'">reads {{ m.channels.map((c) => measureText(c.measurementCode)).join(", ") }}</template>
                <template v-else>{{ m.actuatorType ? actuatorLabels[m.actuatorType as ActuatorType] : m.actionVerb || "custom" }} · limit {{ m.defaultMaxRunMinutes ?? 10 }} min</template>
                <template v-if="m.priceUsd != null"> · ~${{ m.priceUsd }}</template>
              </span>
            </button>
          </div>
        </CardContent>
      </Card>

      <Card v-if="model">
        <CardHeader>
          <CardTitle>3. Details</CardTitle>
          <CardDescription>{{ model.name }}<template v-if="model.wiringNotes"> · wiring: {{ model.wiringNotes }}</template></CardDescription>
        </CardHeader>
        <CardContent class="grid-cols-1 grid gap-4 sm:grid-cols-2">
          <div class="flex flex-col gap-1.5">
            <Label for="nname">Name</Label>
            <Input id="nname" v-model="form.name" maxlength="80" :placeholder="suggestedName" />
            <p class="text-xs text-muted-foreground">Leave empty to use "{{ suggestedName }}".<template v-if="model.channels.length > 1"> Its {{ model.channels.length }} readings are named "{{ suggestedName }} · {{ model.channels[0]!.label }}" and so on.</template></p>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="nboard">Controller board</Label>
            <NativeSelect id="nboard" v-model="form.controllerId" class="w-full">
              <NativeSelectOption value="">None (farm device key)</NativeSelectOption>
              <NativeSelectOption v-for="b in boards" :key="b.id" :value="b.id">{{ b.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <label v-if="model.category === 'SENSOR'" class="flex items-start gap-2 text-sm">
            <input v-model="form.simulated" type="checkbox" class="mt-0.5 size-4 accent-[var(--primary)]">
            <span>Simulated<span class="block text-xs text-muted-foreground">Untick for a real ESP32 or a Farm Lab device, so the built-in simulator leaves it alone.</span></span>
          </label>
          <div v-else class="flex flex-col gap-1.5">
            <Label for="nmax">Safety limit (longest run, minutes)</Label>
            <Input id="nmax" v-model.number="form.maxRunMinutes" type="number" min="1" max="240" required class="w-32" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="nserial">Serial number</Label>
            <Input id="nserial" v-model="form.serialNumber" maxlength="80" placeholder="optional" />
          </div>
          <div class="flex flex-col gap-1.5 sm:col-span-2">
            <Label for="nnotes">Setup and calibration notes</Label>
            <Input id="nnotes" v-model="form.notes" maxlength="1000" placeholder="e.g. dry raw 3000, wet raw 1250; tank height 120 cm" />
          </div>
          <FormError :message="error" class="sm:col-span-2" />
          <div class="flex gap-2 sm:col-span-2">
            <Button type="submit" :disabled="busy || !form.zoneId">Install device</Button>
            <Button type="button" variant="ghost" as-child><NuxtLink to="/devices">Cancel</NuxtLink></Button>
          </div>
        </CardContent>
      </Card>
    </form>
  </div>
</template>
