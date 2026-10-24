<script setup lang="ts">
import { Activity, AlertTriangle, Cpu, Plus, Search } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Actuator, Farm, LifecycleAction, Sensor, SensorAction } from "~/utils/types";
import { actuatorLabels, sensorLabels } from "~/utils/types";
import { actuatorIcons, sensorIcons } from "~/utils/deviceIcons";
import { sensorBadge, sensorDone, sensorNote, sensorOptions } from "~/utils/sensorRepair";
import { actuatorDone, actuatorOptions } from "~/utils/actuatorRepair";

// Every sensor and actuator of the farm on one page, with filters, live status and the technician's actions.

type Row =
  | { kind: "sensor"; key: string; zoneName: string; device: Sensor; status: string }
  | { kind: "actuator"; key: string; zoneName: string; device: Actuator; status: string };

const api = useApi();
const { can } = useAuth();
const route = useRoute();
const router = useRouter();
const farm = ref<Farm | null>(null);
const error = ref("");
const notice = ref("");

async function load() {
  try {
    farm.value = await api<Farm>("/api/farm");
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 4000);

const rows = computed<Row[]>(() => (farm.value?.zones ?? []).flatMap((z) => [
  ...z.sensors.map((s): Row => ({ kind: "sensor", key: `s${s.id}`, zoneName: z.name, device: s, status: sensorBadge(s) })),
  ...z.actuators.map((a): Row => ({ kind: "actuator", key: `a${a.id}`, zoneName: z.name, device: a, status: a.status })),
]));

// ----- filters (kept in the address) -----
const ATTENTION = ["OFFLINE", "FAULT", "MAINTENANCE", "WARNING"];
const filters = reactive({
  zone: route.query.zone ? Number(route.query.zone) : ("" as number | ""),
  kind: (route.query.kind as string) ?? "",
  status: (route.query.status as string) ?? "",
  q: "",
});
watch(() => [filters.zone, filters.kind, filters.status], () => router.replace({
  query: { zone: filters.zone === "" ? undefined : String(filters.zone), kind: filters.kind || undefined, status: filters.status || undefined },
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

const typeLabel = (r: Row) => (r.kind === "sensor" ? `${sensorLabels[r.device.type]} sensor` : actuatorLabels[r.device.type]);
const iconOf = (r: Row) => (r.kind === "sensor" ? sensorIcons[r.device.type] : actuatorIcons[r.device.type]);

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
const ask = (message: string) => window.confirm(message);

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
  if (action === "FAULT" && !ask(`Report ${s.name} as faulty? Its readings will be ignored and its rules paused until it is repaired.`)) return;
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

function remove(r: Row) {
  if (!ask(`Remove ${r.device.name}?${r.kind === "sensor" ? " Its readings and rules are deleted too." : ""}`)) return;
  act(() => api(`/api/${r.kind === "sensor" ? "sensors" : "actuators"}/${r.device.id}`, { method: "DELETE" }), `${r.device.name} removed`);
}

// ----- edit: rename, or change an actuator's safety limit -----
const editOpen = ref(false);
const editForm = reactive({ kind: "sensor" as "sensor" | "actuator", id: 0, name: "", maxRunMinutes: 10 });

function openEdit(r: Row) {
  Object.assign(editForm, { kind: r.kind, id: r.device.id, name: r.device.name,
    maxRunMinutes: r.kind === "actuator" ? r.device.maxRunMinutes : 10 });
  error.value = "";
  editOpen.value = true;
}

async function saveEdit() {
  error.value = "";
  try {
    await (editForm.kind === "sensor"
      ? api(`/api/sensors/${editForm.id}`, { method: "PUT", body: { name: editForm.name } })
      : api(`/api/actuators/${editForm.id}`, { method: "PUT", body: { name: editForm.name, maxRunMinutes: editForm.maxRunMinutes } }));
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

    <div class="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
      <button v-for="s in stats" :key="s.label" type="button"
        class="rounded-xl border bg-card p-4 text-left transition-colors hover:border-primary/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        :class="{ 'border-primary': s.status && filters.status === s.status }"
        @click="filters.status = filters.status === s.status ? '' : s.status">
        <div class="flex items-center gap-3">
          <div class="flex size-9 items-center justify-center rounded-lg bg-accent text-accent-foreground"><component :is="s.icon" class="size-4" /></div>
          <div>
            <div class="text-xl font-semibold tabular-nums">{{ s.value }}</div>
            <div class="text-xs text-muted-foreground">{{ s.label }}</div>
          </div>
        </div>
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
      <p class="pb-2 text-sm text-muted-foreground">{{ shown.length }} of {{ rows.length }}</p>
    </div>

    <Alert v-if="error && !editOpen" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="notice" class="mb-4"><AlertDescription>{{ notice }}</AlertDescription></Alert>

    <!-- Phones: one card per device -->
    <div class="flex flex-col gap-2 md:hidden">
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
          <template v-if="r.kind === 'sensor'">
            <Button v-for="o in sensorOptions[r.device.status]" :key="o.label" size="sm" variant="outline" @click="sensorStep(r.device, o.action)">{{ o.label }}</Button>
          </template>
          <template v-else>
            <Button v-for="o in actuatorOptions[r.device.status] ?? []" :key="o.label" size="sm" variant="outline" @click="actuatorStep(r.device, o.action, o.body)">{{ o.label }}</Button>
          </template>
          <Button size="sm" variant="ghost" @click="openEdit(r)">Edit</Button>
          <Button v-if="r.kind === 'sensor' || r.status === 'DECOMMISSIONED'" size="sm" variant="ghost" @click="remove(r)">Remove</Button>
        </div>
      </div>
    </div>

    <Card class="hidden py-0 md:block">
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
                  <template v-if="r.kind === 'sensor'">
                    <Button v-for="o in sensorOptions[r.device.status]" :key="o.label" size="sm" variant="outline" @click="sensorStep(r.device, o.action)">{{ o.label }}</Button>
                  </template>
                  <template v-else>
                    <Button v-for="o in actuatorOptions[r.device.status] ?? []" :key="o.label" size="sm" variant="outline" @click="actuatorStep(r.device, o.action, o.body)">{{ o.label }}</Button>
                  </template>
                  <Button size="sm" variant="ghost" @click="openEdit(r)">Edit</Button>
                  <Button v-if="r.kind === 'sensor' || r.status === 'DECOMMISSIONED'" size="sm" variant="ghost" @click="remove(r)">Remove</Button>
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
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
