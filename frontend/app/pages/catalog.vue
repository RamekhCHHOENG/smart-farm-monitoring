<script setup lang="ts">
import { Archive, ArchiveRestore, ImageUp, Pencil, Plus, Search, Trash2, X } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { ActuatorType, DeviceCategory, DeviceModel, MeasurementType } from "~/utils/types";
import { actuatorLabels } from "~/utils/types";


definePageMeta({ role: "TECHNICIAN" });
// Master data: every device model the farm can use (with picture, readings or effects, wiring) and the
// measurement types they read. Everyone can browse; the Farm Owner and the Technician manage it.

const api = useApi();
const { can, token } = useAuth();
const models = ref<DeviceModel[]>([]);
const measurements = ref<MeasurementType[]>([]);
const error = ref("");
const notice = ref("");
// results of actions show as a toast at the bottom of the screen, so they are seen wherever the person scrolled to
const toast = useToast();
watch(notice, (v) => { if (v) { toast.success(v); notice.value = ""; } });
const tab = ref<"models" | "measurements">("models");

async function load() {
  try {
    [models.value, measurements.value] = await Promise.all([
      api<DeviceModel[]>("/api/catalog/models"), api<MeasurementType[]>("/api/catalog/measurements"),
    ]);
  } catch (e) {
    error.value = errorMessage(e);
  }
}
onMounted(load);

const measurementOf = (code: string) => measurements.value.find((m) => m.code === code);
const measureText = (code: string) => {
  const m = measurementOf(code);
  return m ? `${m.name} (${m.unit})` : code;
};

// ----- filters -----
const filter = reactive({ category: "" as "" | DeviceCategory, q: "", retired: false });
const shown = computed(() => {
  const q = filter.q.trim().toLowerCase();
  return models.value.filter((m) => (!filter.category || m.category === filter.category)
    && (filter.retired || !m.retired)
    && (!q || [m.name, m.manufacturer ?? "", m.description ?? "", m.interfaceType ?? "",
      ...m.channels.map((c) => measureText(c.measurementCode))].some((x) => x.toLowerCase().includes(q))));
});
const counts = computed(() => ({
  SENSOR: models.value.filter((m) => m.category === "SENSOR" && !m.retired).length,
  ACTUATOR: models.value.filter((m) => m.category === "ACTUATOR" && !m.retired).length,
  CONTROLLER: models.value.filter((m) => m.category === "CONTROLLER" && !m.retired).length,
}));
const categoryLabel: Record<DeviceCategory, string> = { SENSOR: "Sensor", ACTUATOR: "Actuator", CONTROLLER: "Controller board" };

// ----- model editor -----
const WOKWI_PARTS = ["dht22", "hc-sr04", "hx711", "ntc-temperature-sensor", "photoresistor-sensor", "gas-sensor",
  "flame-sensor", "pir-motion-sensor", "big-sound-sensor", "mpu6050", "ds1307", "servo", "stepper-motor", "ks2e-m-dc5",
  "buzzer", "led", "esp32-devkit-v1", "arduino-uno", "arduino-nano", "nano-rp2040-connect"];
const ART_TYPES = ["SOIL_MOISTURE", "TEMPERATURE", "FEED_LEVEL", "HUMIDITY", "LIGHT_LEVEL", "WATER_LEVEL",
  "WATER_PUMP", "FAN", "FEEDER", "HEATER", "MISTER", "LIGHT", "WATER_VALVE"];

const dialog = ref(false);
const photo = ref<File | null>(null);
const blank = () => ({
  id: 0, name: "", manufacturer: "", category: "SENSOR" as DeviceCategory, description: "", interfaceType: "", voltage: "",
  priceUsd: undefined as number | undefined, datasheetUrl: "", wiringNotes: "", wokwiPart: "", artType: "",
  channels: [] as { measurementCode: string; label: string }[],
  actuatorType: "" as string, actionVerb: "", defaultMaxRunMinutes: 10 as number | undefined,
  effects: [] as { measurementCode: string; direction: number }[],
});
const form = reactive(blank());

function open(model?: DeviceModel) {
  Object.assign(form, blank(), model ? {
    ...model, manufacturer: model.manufacturer ?? "", description: model.description ?? "", interfaceType: model.interfaceType ?? "",
    voltage: model.voltage ?? "", datasheetUrl: model.datasheetUrl ?? "", wiringNotes: model.wiringNotes ?? "",
    wokwiPart: model.wokwiPart ?? "", artType: model.artType ?? "", actuatorType: model.actuatorType ?? "",
    actionVerb: model.actionVerb ?? "", channels: model.channels.map((c) => ({ ...c })), effects: model.effects.map((e) => ({ ...e })),
    priceUsd: model.priceUsd ?? undefined, defaultMaxRunMinutes: model.defaultMaxRunMinutes ?? undefined,
  } : {});
  if (!model) form.channels = [{ measurementCode: measurements.value[0]?.code ?? "", label: "" }];
  photo.value = null;
  error.value = "";
  dialog.value = true;
}

function addChannel() {
  form.channels.push({ measurementCode: measurements.value[0]?.code ?? "", label: "" });
}
function addEffect() {
  form.effects.push({ measurementCode: measurements.value[0]?.code ?? "", direction: 1 });
}

/** A built-in actuator kind brings its usual effects; a custom kind starts with none. */
watch(() => form.actuatorType, (kind, old) => {
  if (!dialog.value || kind === old || form.id) return;
  const effects: Record<string, { measurementCode: string; direction: number }[]> = {
    WATER_PUMP: [{ measurementCode: "SOIL_MOISTURE", direction: 1 }], FAN: [{ measurementCode: "TEMPERATURE", direction: -1 }],
    FEEDER: [{ measurementCode: "FEED_LEVEL", direction: 1 }], HEATER: [{ measurementCode: "TEMPERATURE", direction: 1 }],
    MISTER: [{ measurementCode: "HUMIDITY", direction: 1 }], LIGHT: [{ measurementCode: "LIGHT_LEVEL", direction: 1 }],
    WATER_VALVE: [{ measurementCode: "WATER_LEVEL", direction: 1 }],
  };
  form.effects = (effects[kind] ?? []).map((e) => ({ ...e }));
  if (kind) form.artType = kind;
});

async function save() {
  error.value = "";
  const body = {
    ...form,
    priceUsd: form.priceUsd === undefined || (form.priceUsd as unknown) === "" ? null : Number(form.priceUsd),
    channels: form.category === "SENSOR" ? form.channels.map((c) => ({ ...c, label: c.label.trim() || measurementOf(c.measurementCode)?.name || c.measurementCode })) : [],
    effects: form.category === "ACTUATOR" ? form.effects : [],
    actuatorType: form.category === "ACTUATOR" && form.actuatorType ? form.actuatorType : null,
    defaultMaxRunMinutes: form.category === "ACTUATOR" && form.defaultMaxRunMinutes ? form.defaultMaxRunMinutes : null,
  };
  try {
    const saved = await (form.id
      ? api<DeviceModel>(`/api/catalog/models/${form.id}`, { method: "PUT", body })
      : api<DeviceModel>("/api/catalog/models", { method: "POST", body }));
    if (photo.value) await uploadPhoto(saved.id, photo.value);
    dialog.value = false;
    notice.value = `${saved.name} saved`;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function uploadPhoto(id: number, file: File) {
  if (file.size > 2_000_000) throw Object.assign(new Error("The picture must be at most 2 MB"), { data: { detail: "The picture must be at most 2 MB" }, statusCode: 400 });
  await $fetch(`/api/catalog/models/${id}/photo`, {
    method: "PUT", body: file, headers: { "Content-Type": file.type, Authorization: `Bearer ${token.value ?? ""}` },
  });
}

function pickPhoto(e: Event) {
  photo.value = (e.target as HTMLInputElement).files?.[0] ?? null;
}

async function act(fn: () => Promise<unknown>, done: string) {
  error.value = "";
  notice.value = "";
  try {
    await fn();
    notice.value = done;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

const retire = (m: DeviceModel) => act(() => api(`/api/catalog/models/${m.id}/retired`, { method: "PATCH", body: { retired: !m.retired } }),
  `${m.name} ${m.retired ? "is offered again" : "is retired: kept for existing devices, not offered for new ones"}`);
const removeModel = async (m: DeviceModel) => (await useConfirm().confirm({ title: `Delete ${m.name} from the catalog?`, confirmLabel: "Delete", danger: true }))
  && act(() => api(`/api/catalog/models/${m.id}`, { method: "DELETE" }), `${m.name} deleted`);
const removePhoto = (m: DeviceModel) => act(() => api(`/api/catalog/models/${m.id}/photo`, { method: "DELETE" }), `Photo of ${m.name} removed`);

// ----- measurement types -----
const mDialog = ref(false);
const mForm = reactive({ id: 0, name: "", unit: "", minValid: 0, maxValid: 100, typical: 50, builtIn: false });
function openMeasurement(m?: MeasurementType) {
  Object.assign(mForm, m ?? { id: 0, name: "", unit: "", minValid: 0, maxValid: 100, typical: 50, builtIn: false });
  error.value = "";
  mDialog.value = true;
}
async function saveMeasurement() {
  error.value = "";
  const body = { name: mForm.name, unit: mForm.unit, minValid: mForm.minValid, maxValid: mForm.maxValid, typical: mForm.typical };
  try {
    await (mForm.id ? api(`/api/catalog/measurements/${mForm.id}`, { method: "PUT", body }) : api("/api/catalog/measurements", { method: "POST", body }));
    mDialog.value = false;
    notice.value = `${mForm.name} saved`;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}
const removeMeasurement = async (m: MeasurementType) => (await useConfirm().confirm({ title: `Delete the measurement ${m.name}?`, confirmLabel: "Delete", danger: true }))
  && act(() => api(`/api/catalog/measurements/${m.id}`, { method: "DELETE" }), `${m.name} deleted`);
</script>

<template>
  <div>
    <PageHeader title="Device catalog"
      description="Every device model the farm can use: what it measures or does, how it is wired, and what it looks like. New devices are added from this list.">
      <template v-if="can('TECHNICIAN')">
        <Button v-if="tab === 'models'" @click="open()"><Plus /> Add model</Button>
        <Button v-else @click="openMeasurement()"><Plus /> Add measurement</Button>
      </template>
    </PageHeader>

    <div class="mb-4 inline-flex rounded-lg border p-0.5" role="tablist">
      <button type="button" role="tab" :aria-selected="tab === 'models'" class="rounded-md px-4 py-1.5 text-sm font-medium"
        :class="tab === 'models' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'" @click="tab = 'models'">Device models ({{ models.length }})</button>
      <button type="button" role="tab" :aria-selected="tab === 'measurements'" class="rounded-md px-4 py-1.5 text-sm font-medium"
        :class="tab === 'measurements' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'" @click="tab = 'measurements'">Measurement types ({{ measurements.length }})</button>
    </div>

    <Alert v-if="error && !dialog && !mDialog" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <!-- ===== Device models ===== -->
    <template v-if="tab === 'models'">
      <div class="mb-4 flex flex-wrap items-end gap-3">
        <div class="inline-flex rounded-lg border p-0.5">
          <button v-for="c in ([['', 'All'], ['SENSOR', `Sensors (${counts.SENSOR})`], ['ACTUATOR', `Actuators (${counts.ACTUATOR})`], ['CONTROLLER', `Boards (${counts.CONTROLLER})`]] as const)"
            :key="c[0]" type="button" class="rounded-md px-3 py-1.5 text-sm"
            :class="filter.category === c[0] ? 'bg-secondary font-medium text-secondary-foreground' : 'text-muted-foreground'"
            @click="filter.category = c[0]">{{ c[1] }}</button>
        </div>
        <div class="relative min-w-[14rem] flex-1 sm:max-w-sm">
          <Search class="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="filter.q" type="search" class="pl-8" placeholder="Name, maker, what it measures…" aria-label="Search the catalog" />
        </div>
        <label class="flex items-center gap-2 pb-2 text-sm text-muted-foreground">
          <input v-model="filter.retired" type="checkbox" class="size-4 accent-[var(--primary)]"> Show retired
        </label>
        <p class="pb-2 text-sm text-muted-foreground">{{ shown.length }} models</p>
      </div>

      <div class="grid-cols-1 stagger grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
        <article v-for="m in shown" :key="m.id" class="lift flex flex-col gap-2 rounded-xl border bg-card p-3 shadow-soft" :class="{ 'opacity-60': m.retired }">
          <div class="stage rounded-md p-2">
            <DevicePicture :model-id="m.id" :has-picture="m.hasPicture" :wokwi-part="m.wokwiPart" :art-type="m.artType"
              :type="m.channels[0]?.measurementCode ?? m.actuatorType ?? ''" :kind="m.category === 'SENSOR' ? 'sensor' : 'actuator'" :height="130" />
          </div>
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <h3 class="font-medium leading-tight">{{ m.name }}</h3>
              <p class="text-xs text-muted-foreground">{{ m.manufacturer || "—" }} · {{ categoryLabel[m.category] }}</p>
            </div>
            <span v-if="m.retired" class="rounded bg-muted px-1.5 py-0.5 text-[11px]">retired</span>
            <span v-else-if="m.inUse" class="rounded bg-primary/15 px-1.5 py-0.5 text-[11px] text-primary">in use</span>
          </div>
          <p v-if="m.description" class="line-clamp-2 text-xs text-muted-foreground">{{ m.description }}</p>
          <div class="flex flex-wrap gap-1">
            <span v-for="c in m.channels" :key="c.measurementCode + c.label" class="rounded-sm bg-water/12 px-1.5 py-0.5 text-[11px] font-medium text-water">
              reads {{ measureText(c.measurementCode) }}</span>
            <span v-if="m.category === 'ACTUATOR'" class="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] text-amber-900 dark:bg-amber-900/40 dark:text-amber-100">
              {{ m.actuatorType ? actuatorLabels[m.actuatorType as ActuatorType] : m.actionVerb || "custom kind" }}</span>
            <span v-for="e in m.effects" :key="e.measurementCode" class="rounded-full bg-muted px-2 py-0.5 text-[11px]">
              {{ e.direction > 0 ? "raises" : "lowers" }} {{ measurementOf(e.measurementCode)?.name ?? e.measurementCode }}</span>
          </div>
          <p class="text-xs text-muted-foreground">
            {{ [m.interfaceType, m.voltage, m.priceUsd != null ? `~$${m.priceUsd}` : null, m.defaultMaxRunMinutes ? `limit ${m.defaultMaxRunMinutes} min` : null].filter(Boolean).join(" · ") }}
          </p>
          <p v-if="m.wiringNotes" class="text-xs"><b>Wiring:</b> {{ m.wiringNotes }}</p>
          <div v-if="can('TECHNICIAN')" class="mt-auto flex flex-wrap gap-1 border-t pt-2">
            <Button size="sm" variant="outline" @click="open(m)"><Pencil /> Edit</Button>
            <Button size="sm" variant="ghost" @click="retire(m)"><component :is="m.retired ? ArchiveRestore : Archive" /> {{ m.retired ? "Offer again" : "Retire" }}</Button>
            <Button v-if="m.hasPicture" size="sm" variant="ghost" @click="removePhoto(m)"><X /> Photo</Button>
            <Button v-if="!m.builtIn && !m.inUse" size="sm" variant="ghost" :aria-label="`Delete ${m.name}`" @click="removeModel(m)"><Trash2 /></Button>
          </div>
        </article>
      </div>
    </template>

    <!-- ===== Measurement types ===== -->
    <Card v-else class="py-0">
      <CardContent class="px-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="pl-4">Measurement</TableHead>
              <TableHead>Code</TableHead>
              <TableHead>Unit</TableHead>
              <TableHead>Valid range</TableHead>
              <TableHead>Typical</TableHead>
              <TableHead>Used by</TableHead>
              <TableHead class="pr-4" />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="m in measurements" :key="m.id">
              <TableCell class="pl-4 font-medium">{{ m.name }} <span v-if="m.builtIn" class="ml-1 rounded bg-muted px-1.5 py-0.5 text-[11px] font-normal">built in</span></TableCell>
              <TableCell class="font-mono text-xs">{{ m.code }}</TableCell>
              <TableCell>{{ m.unit }}</TableCell>
              <TableCell class="tabular-nums">{{ m.minValid }} – {{ m.maxValid }}</TableCell>
              <TableCell class="tabular-nums">{{ m.typical }}</TableCell>
              <TableCell class="text-xs text-muted-foreground">{{ models.filter((x) => x.channels.some((c) => c.measurementCode === m.code)).length }} model(s)</TableCell>
              <TableCell class="pr-4 text-right">
                <template v-if="can('TECHNICIAN')">
                  <Button size="icon-sm" variant="ghost" :aria-label="`Edit ${m.name}`" @click="openMeasurement(m)"><Pencil /></Button>
                  <Button v-if="!m.builtIn" size="icon-sm" variant="ghost" :aria-label="`Delete ${m.name}`" @click="removeMeasurement(m)"><Trash2 /></Button>
                </template>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <!-- ===== Model dialog ===== -->
    <Dialog v-model:open="dialog">
      <DialogContent class="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ form.id ? `Edit ${form.name}` : "Add a device model" }}</DialogTitle>
          <DialogDescription>A real product the farm can install. Devices made from it get its readings, safety limit and picture.</DialogDescription>
        </DialogHeader>
        <form class="grid grid-cols-2 gap-4" @submit.prevent="save">
          <div class="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
            <Label for="cname">Name</Label>
            <Input id="cname" v-model="form.name" required maxlength="120" placeholder="e.g. MH-Z19B CO₂ sensor" />
          </div>
          <div class="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
            <Label for="ccat">Category</Label>
            <NativeSelect id="ccat" v-model="form.category" class="w-full" :disabled="!!form.id">
              <NativeSelectOption value="SENSOR">Sensor (measures)</NativeSelectOption>
              <NativeSelectOption value="ACTUATOR">Actuator (does something)</NativeSelectOption>
              <NativeSelectOption value="CONTROLLER">Controller board (ESP32...)</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
            <Label for="cmaker">Manufacturer</Label>
            <Input id="cmaker" v-model="form.manufacturer" maxlength="80" />
          </div>
          <div class="col-span-2 grid grid-cols-3 gap-2 sm:col-span-1">
            <div class="flex flex-col gap-1.5"><Label for="ciface">Interface</Label><Input id="ciface" v-model="form.interfaceType" maxlength="60" placeholder="I2C" /></div>
            <div class="flex flex-col gap-1.5"><Label for="cvolt">Voltage</Label><Input id="cvolt" v-model="form.voltage" maxlength="40" placeholder="3.3 V" /></div>
            <div class="flex flex-col gap-1.5"><Label for="cprice">Price $</Label><Input id="cprice" v-model.number="form.priceUsd" type="number" min="0" step="0.5" /></div>
          </div>
          <div class="col-span-2 flex flex-col gap-1.5">
            <Label for="cdesc">Description</Label>
            <Input id="cdesc" v-model="form.description" maxlength="1000" placeholder="What it is for, accuracy, where to mount it" />
          </div>

          <!-- readings -->
          <div v-if="form.category === 'SENSOR'" class="col-span-2 flex flex-col gap-2 rounded-lg border p-3">
            <div class="flex items-center"><span class="text-sm font-medium">Readings it gives</span>
              <Button type="button" size="sm" variant="outline" class="ml-auto" @click="addChannel"><Plus /> Reading</Button></div>
            <div v-for="(c, i) in form.channels" :key="i" class="flex items-center gap-2">
              <NativeSelect v-model="c.measurementCode" class="min-w-0 flex-1" :aria-label="`Reading ${i + 1} measures`">
                <NativeSelectOption v-for="m in measurements" :key="m.code" :value="m.code">{{ m.name }} ({{ m.unit }})</NativeSelectOption>
              </NativeSelect>
              <Input v-model="c.label" class="w-36" maxlength="40" placeholder="Label" :aria-label="`Reading ${i + 1} label`" />
              <Button type="button" size="icon-sm" variant="ghost" :aria-label="`Remove reading ${i + 1}`" @click="form.channels.splice(i, 1)"><X /></Button>
            </div>
            <p class="text-xs text-muted-foreground">A DHT22 gives two: temperature and humidity. Missing a measurement? Add it under Measurement types.</p>
          </div>

          <!-- actuator -->
          <div v-if="form.category === 'ACTUATOR'" class="col-span-2 grid grid-cols-2 gap-3 rounded-lg border p-3">
            <div class="flex flex-col gap-1.5">
              <Label for="ckind">Kind</Label>
              <NativeSelect id="ckind" v-model="form.actuatorType" class="w-full">
                <NativeSelectOption v-for="(label, key) in actuatorLabels" :key="key" :value="key">{{ label }}</NativeSelectOption>
                <NativeSelectOption value="">A kind of its own…</NativeSelectOption>
              </NativeSelect>
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="cmax">Safety limit (minutes)</Label>
              <Input id="cmax" v-model.number="form.defaultMaxRunMinutes" type="number" min="1" max="240" />
            </div>
            <div v-if="!form.actuatorType" class="col-span-2 flex flex-col gap-1.5">
              <Label for="cverb">What it does, as a verb (alerts and logs)</Label>
              <Input id="cverb" v-model="form.actionVerb" maxlength="40" placeholder="e.g. Ventilating, Sounding, Shading" />
            </div>
            <div class="col-span-2 flex flex-col gap-2">
              <div class="flex items-center"><span class="text-sm font-medium">While it runs</span>
                <Button type="button" size="sm" variant="outline" class="ml-auto" @click="addEffect"><Plus /> Effect</Button></div>
              <div v-for="(e, i) in form.effects" :key="i" class="flex items-center gap-2">
                <NativeSelect v-model.number="e.direction" class="w-32" :aria-label="`Effect ${i + 1} direction`">
                  <NativeSelectOption :value="1">raises</NativeSelectOption>
                  <NativeSelectOption :value="-1">lowers</NativeSelectOption>
                </NativeSelect>
                <NativeSelect v-model="e.measurementCode" class="min-w-0 flex-1" :aria-label="`Effect ${i + 1} measurement`">
                  <NativeSelectOption v-for="m in measurements" :key="m.code" :value="m.code">{{ m.name }}</NativeSelectOption>
                </NativeSelect>
                <Button type="button" size="icon-sm" variant="ghost" :aria-label="`Remove effect ${i + 1}`" @click="form.effects.splice(i, 1)"><X /></Button>
              </div>
              <p class="text-xs text-muted-foreground">The simulator and the Farm Lab use this, e.g. a CO₂ vent lowers CO₂.</p>
            </div>
          </div>

          <!-- look and docs -->
          <div class="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
            <Label for="cwokwi">Wokwi part drawing</Label>
            <NativeSelect id="cwokwi" v-model="form.wokwiPart" class="w-full">
              <NativeSelectOption value="">None</NativeSelectOption>
              <NativeSelectOption v-for="p in WOKWI_PARTS" :key="p" :value="p">{{ p }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
            <Label for="cart">Farm drawing (if no photo or Wokwi part)</Label>
            <NativeSelect id="cart" v-model="form.artType" class="w-full">
              <NativeSelectOption value="">Generic</NativeSelectOption>
              <NativeSelectOption v-for="a in ART_TYPES" :key="a" :value="a">{{ a.toLowerCase().replace("_", " ") }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="col-span-2 flex items-center gap-3 rounded-lg border p-3">
            <div class="h-[90px] w-[120px] shrink-0 rounded-md bg-muted/50">
              <DevicePicture :model-id="form.id || null" :has-picture="false" :wokwi-part="form.wokwiPart || null" :art-type="form.artType || null"
                :type="form.channels[0]?.measurementCode ?? form.actuatorType" :kind="form.category === 'SENSOR' ? 'sensor' : 'actuator'" :height="90" />
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="cphoto" class="flex items-center gap-1.5"><ImageUp class="size-4" /> Photo (PNG, JPEG, WebP; max 2 MB)</Label>
              <input id="cphoto" type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="text-sm" @change="pickPhoto">
              <p class="text-xs text-muted-foreground">A photo is shown instead of the drawing.</p>
            </div>
          </div>
          <div class="col-span-2 flex flex-col gap-1.5">
            <Label for="cwire">Wiring notes</Label>
            <Input id="cwire" v-model="form.wiringNotes" maxlength="1000" placeholder="e.g. SDA to GPIO 21, SCL to GPIO 22" />
          </div>
          <div class="col-span-2 flex flex-col gap-1.5">
            <Label for="cdata">Datasheet link</Label>
            <Input id="cdata" v-model="form.datasheetUrl" type="url" maxlength="500" placeholder="https://…" />
          </div>
          <FormError :message="error" class="col-span-2" />
          <DialogFooter class="col-span-2"><Button type="submit">Save model</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <!-- ===== Measurement dialog ===== -->
    <Dialog v-model:open="mDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ mForm.id ? `Edit ${mForm.name}` : "Add a measurement type" }}</DialogTitle>
          <DialogDescription>{{ mForm.builtIn ? "Built in: only the name and the typical value can change." : "Something a sensor can read, with its unit and the values it can physically report." }}</DialogDescription>
        </DialogHeader>
        <form class="grid grid-cols-2 gap-4" @submit.prevent="saveMeasurement">
          <div class="col-span-2 flex flex-col gap-1.5"><Label for="mname">Name</Label><Input id="mname" v-model="mForm.name" required maxlength="60" placeholder="e.g. Soil salinity" /></div>
          <div class="flex flex-col gap-1.5"><Label for="munit">Unit</Label><Input id="munit" v-model="mForm.unit" required maxlength="20" :disabled="mForm.builtIn" placeholder="dS/m" /></div>
          <div class="flex flex-col gap-1.5"><Label for="mtyp">Typical value</Label><Input id="mtyp" v-model.number="mForm.typical" type="number" step="any" required /></div>
          <div class="flex flex-col gap-1.5"><Label for="mmin">Lowest valid</Label><Input id="mmin" v-model.number="mForm.minValid" type="number" step="any" required :disabled="mForm.builtIn" /></div>
          <div class="flex flex-col gap-1.5"><Label for="mmax">Highest valid</Label><Input id="mmax" v-model.number="mForm.maxValid" type="number" step="any" required :disabled="mForm.builtIn" /></div>
          <p class="col-span-2 text-xs text-muted-foreground">Readings outside the valid range are rejected as impossible (HTTP 422), and rules can only use thresholds inside it.</p>
          <FormError :message="error" class="col-span-2" />
          <DialogFooter class="col-span-2"><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
