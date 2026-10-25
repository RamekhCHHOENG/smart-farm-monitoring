<script setup lang="ts">
import { Activity, AlertTriangle, CircleAlert, Cpu, Image, List, Pencil, Plus, Search, Trash2, TriangleAlert, Unplug, Wrench } from "lucide-vue-next";
import type { MenuAction } from "~/utils/menu";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Actuator, ControllerBoard, Farm, FarmDevice, LifecycleAction, Sensor, SensorAction } from "~/utils/types";
import { actuatorKind, sensorKind } from "~/utils/types";
import { actuatorIcon, sensorIcon } from "~/utils/deviceIcons";
import { sensorBadge, sensorDone, sensorNote, sensorOptions } from "~/utils/sensorRepair";
import { actuatorDone, actuatorOptions } from "~/utils/actuatorRepair";

// Every sensor and actuator of the farm on one page, with filters, live status and the technician's actions.

type Row =
  | { kind: "sensor"; key: string; zoneName: string; lab: boolean; device: Sensor; status: string }
  | { kind: "actuator"; key: string; zoneName: string; lab: boolean; device: Actuator; status: string };

const api = useApi();
const { can } = useAuth();
const route = useRoute();
const router = useRouter();
const farm = ref<Farm | null>(null);
const boards = ref<ControllerBoard[]>([]);
const error = ref("");
const notice = ref("");
// results of actions show as a toast at the bottom of the screen, so they are seen wherever the person scrolled to
const toast = useToast();
watch(notice, (v) => { if (v) { toast.success(v); notice.value = ""; } });
watch(error, (v) => { if (v && !editOpen.value) toast.error(v); });

async function load() {
  try {
    farm.value = await api<Farm>("/api/farm");
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 4000);
onMounted(async () => {
  try {
    boards.value = await api<ControllerBoard[]>("/api/controllers");
  } catch {
    // boards are optional here
  }
});

/** The physical unit (catalog model, board, serial) behind each reading or output. */
const units = computed(() => new Map((farm.value?.zones ?? []).flatMap((z) => z.devices).map((d) => [d.id, d])));
const unitOf = (r: Row): FarmDevice | undefined => (r.device.deviceId ? units.value.get(r.device.deviceId) : undefined);

const allRows = computed<Row[]>(() => (farm.value?.zones ?? []).flatMap((z) => [
  ...z.sensors.map((s): Row => ({ kind: "sensor", key: `s${s.id}`, zoneName: z.name, lab: z.lab, device: s, status: sensorBadge(s) })),
  ...z.actuators.map((a): Row => ({ kind: "actuator", key: `a${a.id}`, zoneName: z.name, lab: z.lab, device: a, status: a.status })),
]));
// Farm Lab test devices are hidden unless asked for, like on the dashboard
const showLab = ref(route.query.lab === "1");
const labCount = computed(() => allRows.value.filter((r) => r.lab).length);
const rows = computed(() => allRows.value.filter((r) => showLab.value || !r.lab));

// Pictures (default) or a compact table
const view = ref<"pictures" | "table">(route.query.view === "table" ? "table" : "pictures");

// ----- filters (kept in the address) -----
const ATTENTION = ["OFFLINE", "FAULT", "MAINTENANCE", "WARNING"];
const filters = reactive({
  zone: route.query.zone ? Number(route.query.zone) : ("" as number | ""),
  kind: (route.query.kind as string) ?? "",
  status: (route.query.status as string) ?? "",
  q: "",
});
watch(() => [filters.zone, filters.kind, filters.status, view.value, showLab.value], () => router.replace({
  query: {
    zone: filters.zone === "" ? undefined : String(filters.zone), kind: filters.kind || undefined, status: filters.status || undefined,
    view: view.value === "table" ? "table" : undefined, lab: showLab.value ? "1" : undefined,
  },
}));

const shown = computed(() => {
  const q = filters.q.trim().toLowerCase();
  return rows.value.filter((r) =>
    (filters.zone === "" || r.device.zoneId === filters.zone)
    && (!filters.kind || r.kind === filters.kind)
    && (!filters.status || (filters.status === "ATTENTION" ? ATTENTION.includes(r.status) : r.status === filters.status))
    && (!q || `${r.device.name} #${r.device.id} ${typeLabel(r)} ${r.zoneName}`.toLowerCase().includes(q)));
});

const stats = computed(() => {
  const sensors = rows.value.filter((r) => r.kind === "sensor");
  const actuators = rows.value.filter((r) => r.kind === "actuator" && r.status !== "DECOMMISSIONED");
  return [
    { label: "Devices", value: rows.value.length, icon: Cpu, status: "" },
    { label: "Sensors online", value: `${sensors.filter((r) => r.status === "ONLINE").length}/${sensors.length}`, icon: Activity, status: "ONLINE" },
    { label: "Actuators running", value: actuators.filter((r) => r.status === "RUNNING").length, icon: Cpu, status: "RUNNING" },
    { label: "Need attention", value: rows.value.filter((r) => ATTENTION.includes(r.status)).length, icon: AlertTriangle, status: "ATTENTION" },
  ];
});

const typeLabel = (r: Row) => (r.kind === "sensor" ? `${sensorKind(r.device)} sensor` : actuatorKind(r.device));
const iconOf = (r: Row) => (r.kind === "sensor" ? sensorIcon(r.device.type) : actuatorIcon(r.device.type));

function readingOf(r: Row) {
  if (r.kind === "sensor") {
    const s = r.device;
    return s.latest ? `${s.latest.value.toFixed(s.type === "LIGHT_LEVEL" ? 0 : 1)} ${s.unit}` : "No data";
  }
  const a = r.device;
  return a.status === "RUNNING" ? `${minutesLeft(a.runningUntil)}${a.startedByRuleId ? " · by a rule" : ""}` : `max ${a.maxRunMinutes} min`;
}

function noteOf(r: Row) {
  return r.kind === "sensor" ? sensorNote(r.device) : "";
}

// ----- actions -----
const { confirm } = useConfirm();

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

async function sensorStep(s: Sensor, action: SensorAction) {
  if (action === "FAULT" && !(await confirm({ title: `Report ${s.name} as faulty?`,
    message: "Its readings will be ignored and its rules paused until it is repaired.", confirmLabel: "Report fault", danger: true }))) return;
  error.value = "";
  notice.value = "";
  try {
    const updated = await api<Sensor>(`/api/sensors/${s.id}/lifecycle/${action}`, { method: "POST" });
    if (action === "COMPLETE_REPAIR" && updated.status === "MAINTENANCE") {
      error.value = `${s.name}: test failed, no valid reading since the repair began. Check power, cable and Wi-Fi, then try again.`;
    } else {
      notice.value = `${s.name}: ${sensorDone[action]}`;
    }
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

function actuatorStep(a: Actuator, action: LifecycleAction, body?: { testPassed?: boolean }) {
  const done = action === "COMPLETE_REPAIR" && body?.testPassed === false ? "test failed, still in maintenance" : actuatorDone[action];
  act(() => api(`/api/actuators/${a.id}/lifecycle/${action}`, { method: "POST", body: body ?? {} }), `${a.name}: ${done}`);
}

async function remove(r: Row) {
  if (!(await confirm({ title: `Remove ${r.device.name}?`, message: r.kind === "sensor" ? "Its readings and rules are deleted too." : undefined,
    confirmLabel: "Remove", danger: true }))) return;
  act(() => api(`/api/${r.kind === "sensor" ? "sensors" : "actuators"}/${r.device.id}`, { method: "DELETE" }), `${r.device.name} removed`);
}

// One visible button when something needs doing (a fault, a repair); everything else in the ⋯ menu.
const abnormal = (status: string) => ["OFFLINE", "FAULT", "MAINTENANCE"].includes(status);
const stepIcon = (action: string) => (action === "FAULT" ? CircleAlert : action === "OFFLINE" ? Unplug : Wrench);
function stepsOf(r: Row): MenuAction[] {
  return r.kind === "sensor"
    ? sensorOptions[(r.device as Sensor).status].map((o) => ({ label: o.label, icon: stepIcon(o.action), run: () => sensorStep(r.device as Sensor, o.action) }))
    : (actuatorOptions[(r.device as Actuator).status] ?? []).map((o) => ({ label: o.label, icon: stepIcon(o.action), run: () => actuatorStep(r.device as Actuator, o.action, o.body) }));
}
const primaryOf = (r: Row) => (abnormal(r.device.status) ? stepsOf(r)[0] ?? null : null);
function menuOf(r: Row): MenuAction[] {
  const steps = stepsOf(r);
  return [
    ...(primaryOf(r) ? steps.slice(1) : steps),
    { label: "Edit", icon: Pencil, separated: true, run: () => openEdit(r) },
    ...(r.kind === "sensor" || r.status === "DECOMMISSIONED" ? [{ label: "Remove", icon: Trash2, danger: true, run: () => remove(r) }] : []),
  ];
}

// ----- edit: rename, or change an actuator's safety limit -----
const editOpen = ref(false);
const editForm = reactive({ kind: "sensor" as "sensor" | "actuator", id: 0, name: "", maxRunMinutes: 10,
  unitId: 0, unitName: "", modelName: "", controllerId: "" as number | "", serialNumber: "", notes: "" });

function openEdit(r: Row) {
  const unit = unitOf(r);
  Object.assign(editForm, { kind: r.kind, id: r.device.id, name: r.device.name,
    maxRunMinutes: r.kind === "actuator" ? r.device.maxRunMinutes : 10,
    unitId: unit?.id ?? 0, unitName: unit?.name ?? "", modelName: unit?.modelName ?? "",
    controllerId: unit?.controllerId ?? "", serialNumber: unit?.serialNumber ?? "", notes: unit?.notes ?? "" });
  error.value = "";
  editOpen.value = true;
}

async function saveEdit() {
  error.value = "";
  try {
    await (editForm.kind === "sensor"
      ? api(`/api/sensors/${editForm.id}`, { method: "PUT", body: { name: editForm.name } })
      : api(`/api/actuators/${editForm.id}`, { method: "PUT", body: { name: editForm.name, maxRunMinutes: editForm.maxRunMinutes } }));
    if (editForm.unitId) {
      await api(`/api/farm-devices/${editForm.unitId}`, { method: "PUT", body: {
        name: editForm.unitName.trim() || editForm.name, serialNumber: editForm.serialNumber, notes: editForm.notes,
        controllerId: editForm.controllerId === "" ? null : editForm.controllerId,
      } });
    }
    editOpen.value = false;
    notice.value = "Device updated";
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

const addLink = computed(() => ({ path: "/devices/new", query: filters.zone === "" ? {} : { zone: String(filters.zone) } }));
</script>

<template>
  <div>
    <PageHeader title="Devices" description="Every sensor and actuator on the farm: live status, filters, and the technician's repair actions.">
      <Button v-if="can('TECHNICIAN')" as-child><NuxtLink :to="addLink"><Plus /> Add device</NuxtLink></Button>
    </PageHeader>

    <div class="mb-5 grid grid-cols-2 gap-y-4 sm:flex sm:flex-wrap sm:divide-x">
      <button v-for="s in stats" :key="s.label" type="button"
        class="group relative pr-6 pb-1 text-left sm:px-6 sm:first:pl-0 focus-visible:outline-none"
        :title="s.status ? 'Show only these' : 'Show all'"
        @click="filters.status = filters.status === s.status ? '' : s.status">
        <div class="eyebrow group-hover:text-foreground" :class="{ '!text-primary': s.status && filters.status === s.status }">{{ s.label }}</div>
        <div class="num mt-1 text-3xl leading-none font-semibold" :class="{ 'text-warning': s.status === 'ATTENTION' && Number(s.value) > 0 }">{{ s.value }}</div>
        <span v-if="s.status && filters.status === s.status" class="absolute right-6 -bottom-1 left-0 h-0.5 bg-primary sm:left-6 sm:first:left-0" />
      </button>
    </div>

    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div class="flex flex-col gap-1.5">
        <Label for="dzone">Zone</Label>
        <NativeSelect id="dzone" v-model="filters.zone" class="w-52">
          <NativeSelectOption value="">All zones</NativeSelectOption>
          <NativeSelectOption v-for="z in farm?.zones ?? []" :key="z.id" :value="z.id">{{ z.name }}</NativeSelectOption>
        </NativeSelect>
      </div>
      <div class="flex flex-col gap-1.5">
        <Label for="dkind">Kind</Label>
        <NativeSelect id="dkind" v-model="filters.kind" class="w-36">
          <NativeSelectOption value="">All</NativeSelectOption>
          <NativeSelectOption value="sensor">Sensors</NativeSelectOption>
          <NativeSelectOption value="actuator">Actuators</NativeSelectOption>
        </NativeSelect>
      </div>
      <div class="flex flex-col gap-1.5">
        <Label for="dstatus">Status</Label>
        <NativeSelect id="dstatus" v-model="filters.status" class="w-44">
          <NativeSelectOption value="">Any status</NativeSelectOption>
          <NativeSelectOption value="ATTENTION">Needs attention</NativeSelectOption>
          <NativeSelectOption v-for="s in ['ONLINE', 'OFFLINE', 'FAULT', 'MAINTENANCE', 'IDLE', 'RUNNING', 'DECOMMISSIONED']" :key="s" :value="s">{{ s }}</NativeSelectOption>
        </NativeSelect>
      </div>
      <div class="relative min-w-[12rem] flex-1 sm:max-w-xs">
        <Label for="dsearch" class="sr-only">Search</Label>
        <Search class="absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input id="dsearch" v-model="filters.q" type="search" class="pl-8" placeholder="Name, #id or type" />
      </div>
      <label v-if="labCount" class="flex items-center gap-2 pb-2 text-sm text-muted-foreground">
        <input v-model="showLab" type="checkbox" class="size-4 accent-[var(--primary)]"> Farm Lab devices ({{ labCount }})
      </label>
      <p class="pb-2 text-sm text-muted-foreground">{{ shown.length }} of {{ rows.length }}</p>
      <div class="ml-auto inline-flex rounded-lg border p-0.5" role="radiogroup" aria-label="View">
        <button type="button" role="radio" :aria-checked="view === 'pictures'" class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm"
          :class="view === 'pictures' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'" @click="view = 'pictures'"><Image class="size-4" /> Pictures</button>
        <button type="button" role="radio" :aria-checked="view === 'table'" class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm"
          :class="view === 'table' ? 'bg-primary text-primary-foreground' : 'text-muted-foreground'" @click="view = 'table'"><List class="size-4" /> Table</button>
      </div>
    </div>

    <Alert v-if="error && !editOpen" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <!-- Pictures: every device drawn as the physical thing, moving with its live state -->
    <div v-if="view === 'pictures'" class="grid-cols-1 stagger grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
      <article v-for="r in shown" :key="`p${r.key}`" class="lift flex flex-col gap-2.5 rounded-[1.4rem] border bg-card p-3.5 shadow-soft"
        :class="{ 'border-destructive/60': r.status === 'FAULT', 'border-warning': r.status === 'MAINTENANCE' || r.status === 'OFFLINE', 'ring-2 ring-primary/40': r.status === 'RUNNING' }">
        <div class="stage relative rounded-2xl p-2">
          <StatusBadge :status="r.status" class="absolute top-2.5 left-2.5 z-10 bg-card/90 backdrop-blur" />
          <DevicePicture v-if="r.kind === 'sensor'" :model-id="unitOf(r)?.modelId" :has-picture="unitOf(r)?.hasPicture" :wokwi-part="unitOf(r)?.wokwiPart"
            :art-type="unitOf(r)?.artType" :type="r.device.type" kind="sensor" :value="r.device.latest?.value ?? null"
            :offline="r.device.status === 'OFFLINE'" :fault="r.device.status === 'FAULT' || r.device.status === 'MAINTENANCE'" :height="130" />
          <DevicePicture v-else :model-id="unitOf(r)?.modelId" :has-picture="unitOf(r)?.hasPicture" :wokwi-part="unitOf(r)?.wokwiPart"
            :art-type="unitOf(r)?.artType" :type="r.device.type" kind="actuator" :on="r.device.status === 'RUNNING'"
            :offline="r.device.status === 'OFFLINE' || r.device.status === 'DECOMMISSIONED'" :fault="r.device.status === 'FAULT' || r.device.status === 'MAINTENANCE'" :height="130" />
        </div>
        <div class="flex items-start gap-2">
          <div class="min-w-0 flex-1">
            <div class="truncate font-medium">{{ r.device.name }}</div>
            <div class="truncate text-xs text-muted-foreground"><span class="font-mono">#{{ r.device.id }}</span> · {{ typeLabel(r) }} · {{ r.zoneName }}<template v-if="r.lab"> · LAB</template></div>
            <div v-if="unitOf(r)" class="truncate text-xs text-muted-foreground">{{ unitOf(r)!.modelName }}<template v-if="unitOf(r)!.controllerName"> · board {{ unitOf(r)!.controllerName }}</template></div>
          </div>
        </div>
        <div class="num text-2xl leading-none">{{ readingOf(r) }}
          <span v-if="r.kind === 'sensor'" class="text-xs font-normal text-muted-foreground">{{ timeAgo(r.device.lastSeenAt) }}</span>
        </div>
        <p v-if="noteOf(r)" class="flex items-start gap-1 text-xs" :class="['OFFLINE', 'FAULT', 'MAINTENANCE'].includes(r.status) ? 'text-warning' : 'text-muted-foreground'">
          <TriangleAlert v-if="['OFFLINE', 'FAULT', 'MAINTENANCE'].includes(r.status)" class="mt-px size-3 shrink-0" />{{ noteOf(r) }}</p>
        <div v-if="can('TECHNICIAN')" class="mt-auto flex flex-wrap gap-1 border-t pt-2">
          <Button v-if="primaryOf(r)" size="sm" @click="primaryOf(r)!.run()">{{ primaryOf(r)!.label }}</Button>
          <span class="ml-auto"><ActionMenu :actions="menuOf(r)" :label="`More actions for ${r.device.name}`" /></span>
        </div>
      </article>
      <p v-if="!shown.length" class="col-span-full py-8 text-center text-muted-foreground">
        {{ rows.length ? "No device matches these filters." : "No devices yet." }}
      </p>
    </div>

    <!-- Phones: one card per device -->
    <div v-if="view === 'table'" class="flex flex-col gap-2 md:hidden">
      <div v-for="r in shown" :key="`m${r.key}`" class="flex flex-col gap-2 rounded-lg border bg-card p-3">
        <div class="flex items-center gap-2">
          <component :is="iconOf(r)" class="size-4 shrink-0 text-muted-foreground" />
          <span class="font-medium">{{ r.device.name }}</span>
          <span class="text-xs text-muted-foreground">#{{ r.device.id }}</span>
          <span class="ml-auto"><StatusBadge :status="r.status" /></span>
        </div>
        <p class="text-sm text-muted-foreground">{{ typeLabel(r) }} · {{ r.zoneName }} · {{ readingOf(r) }}</p>
        <p v-if="noteOf(r)" class="text-xs text-muted-foreground">{{ noteOf(r) }}</p>
        <div v-if="can('TECHNICIAN')" class="flex flex-wrap gap-1">
          <Button v-if="primaryOf(r)" size="sm" @click="primaryOf(r)!.run()">{{ primaryOf(r)!.label }}</Button>
          <span class="ml-auto"><ActionMenu :actions="menuOf(r)" :label="`More actions for ${r.device.name}`" /></span>
        </div>
      </div>
    </div>

    <Card v-if="view === 'table'" class="hidden py-0 md:block">
      <CardContent class="px-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="pl-4">Device</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Zone</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Reading / state</TableHead>
              <TableHead class="pr-4 text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="r in shown" :key="r.key">
              <TableCell class="pl-4">
                <div class="flex items-center gap-2">
                  <component :is="iconOf(r)" class="size-4 shrink-0 text-muted-foreground" />
                  <span class="font-medium">{{ r.device.name }}</span>
                  <span class="text-xs text-muted-foreground">#{{ r.device.id }}</span>
                </div>
              </TableCell>
              <TableCell>
                {{ typeLabel(r) }}
                <div class="text-xs text-muted-foreground">
                  <template v-if="unitOf(r)">{{ unitOf(r)!.modelName }} · </template>
                  <template v-if="r.kind === 'sensor'">{{ r.device.simulated ? "simulated" : "real device" }}</template>
                  <template v-else>safety limit {{ r.device.maxRunMinutes }} min</template>
                </div>
              </TableCell>
              <TableCell>{{ r.zoneName }}</TableCell>
              <TableCell>
                <StatusBadge :status="r.status" />
                <div v-if="noteOf(r)" class="mt-1 max-w-56 text-xs text-muted-foreground">{{ noteOf(r) }}</div>
              </TableCell>
              <TableCell class="tabular-nums">
                {{ readingOf(r) }}
                <div v-if="r.kind === 'sensor'" class="text-xs text-muted-foreground">{{ timeAgo(r.device.lastSeenAt) }}</div>
              </TableCell>
              <TableCell class="pr-4">
                <div v-if="can('TECHNICIAN')" class="flex flex-wrap justify-end gap-1">
                  <Button v-if="primaryOf(r)" size="sm" @click="primaryOf(r)!.run()">{{ primaryOf(r)!.label }}</Button>
                  <span class="ml-auto"><ActionMenu :actions="menuOf(r)" :label="`More actions for ${r.device.name}`" /></span>
                </div>
              </TableCell>
            </TableRow>
            <TableRow v-if="!shown.length">
              <TableCell colspan="6" class="py-8 text-center text-muted-foreground">
                {{ rows.length ? "No device matches these filters." : "No devices yet." }}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Dialog v-model:open="editOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ editForm.kind === "sensor" ? "Edit sensor" : "Edit actuator" }}</DialogTitle>
          <DialogDescription>Renaming keeps the #id, so the ESP32 does not need changes.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveEdit">
          <div class="flex flex-col gap-1.5">
            <Label for="dename">Name</Label>
            <Input id="dename" v-model="editForm.name" required maxlength="80" />
          </div>
          <div v-if="editForm.kind === 'actuator'" class="flex flex-col gap-1.5">
            <Label for="demax">Safety limit (minutes)</Label>
            <Input id="demax" v-model.number="editForm.maxRunMinutes" type="number" min="1" max="240" required />
          </div>
          <fieldset v-if="editForm.unitId" class="flex flex-col gap-3 rounded-lg border p-3">
            <legend class="px-1 text-sm font-medium">Physical device · {{ editForm.modelName }}</legend>
            <div class="flex flex-col gap-1.5">
              <Label for="duname">Device name</Label>
              <Input id="duname" v-model="editForm.unitName" maxlength="80" />
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="duboard">Controller board</Label>
              <NativeSelect id="duboard" v-model="editForm.controllerId" class="w-full">
                <NativeSelectOption value="">None (farm device key)</NativeSelectOption>
                <NativeSelectOption v-for="b in boards" :key="b.id" :value="b.id">{{ b.name }}</NativeSelectOption>
              </NativeSelect>
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="duserial">Serial number</Label>
              <Input id="duserial" v-model="editForm.serialNumber" maxlength="80" />
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="dunotes">Setup and calibration notes</Label>
              <Input id="dunotes" v-model="editForm.notes" maxlength="1000" />
            </div>
          </fieldset>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
