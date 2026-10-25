<script setup lang="ts">
import { CircleAlert, Pencil, Plus, Search, Settings, Trash2, Unplug, Wand2, Wrench } from "lucide-vue-next";
import type { MenuAction } from "~/utils/menu";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Actuator, ActuatorType, Farm, LifecycleAction, Sensor, SensorAction, SensorType, Zone } from "~/utils/types";
import { actuatorHints, actuatorKind, actuatorLabels, defaultRunMinutes, sensorHints, sensorKind, sensorLabels } from "~/utils/types";
import { sensorBadge, sensorDone, sensorNote, sensorOptions } from "~/utils/sensorRepair";
import { actuatorDone, actuatorOptions } from "~/utils/actuatorRepair";

const api = useApi();
const { can, isCompany } = useAuth();
const farm = ref<Farm | null>(null);
const error = ref("");
const notice = ref("");
// results of actions show as a toast at the bottom of the screen, so they are seen wherever the person scrolled to
const toast = useToast();
watch(notice, (v) => { if (v) { toast.success(v); notice.value = ""; } });
watch(error, (v) => { if (v && !(farmDialog.value || zoneDialog.value || deviceDialog.value || editDialog.value)) toast.error(v); });

async function load() {
  try {
    farm.value = await api<Farm>("/api/farm");
  } catch (e) {
    error.value = errorMessage(e);
  }
}
// Refreshes by itself, so a technician waiting for a repaired sensor's first reading sees it arrive.
usePolling(load, 4000);

const { confirm } = useConfirm();

async function act(fn: () => Promise<unknown>, done?: string) {
  error.value = "";
  notice.value = "";
  try {
    await fn();
    if (done) notice.value = done;
    await load();
    return true;
  } catch (e) {
    error.value = errorMessage(e);
    return false;
  }
}

// A farm can have hundreds of zones: search by name, crop or device, 10 zones at a time.
const search = ref("");
const zoneLimit = ref(10);
watch(search, () => (zoneLimit.value = 10));
const filteredZones = computed(() => {
  const q = search.value.trim().toLowerCase();
  return (farm.value?.zones ?? []).filter((z) => !q || [z.name, z.detail, ...z.sensors.map((s) => s.name), ...z.actuators.map((a) => a.name)]
    .some((x) => (x ?? "").toLowerCase().includes(q)));
});

// ----- farm settings -----
const farmDialog = ref(false);
const farmForm = reactive({ name: "", location: "" });

function openFarm() {
  Object.assign(farmForm, { name: farm.value?.name ?? "", location: farm.value?.location ?? "" });
  error.value = "";
  farmDialog.value = true;
}

// Only the farm's own owner decides; company staff working on the farm see the setting but cannot change it.
const supportBusy = ref(false);
async function setSupportAccess(allowed: boolean) {
  supportBusy.value = true;
  try {
    farm.value = await api<Farm>("/api/farm/support-access", { method: "PUT", body: { allowed } });
    toast.success(allowed ? "Smart Farm support can open this farm" : "Smart Farm support can no longer open this farm");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    supportBusy.value = false;
  }
}

async function saveFarm() {
  if (await act(() => api("/api/farm", { method: "PUT", body: { ...farmForm } }), "Farm settings saved")) {
    farmDialog.value = false;
  }
}

// ----- zone dialog (UC-1) -----
const zoneDialog = ref(false);
const zoneForm = reactive({ id: 0, type: "CROP", name: "", cropName: "", birdCount: 0 });

function openZone(zone?: Zone) {
  Object.assign(zoneForm, zone
    ? { id: zone.id, type: zone.type, name: zone.name, cropName: zone.cropName ?? "", birdCount: zone.birdCount ?? 0 }
    : { id: 0, type: "CROP", name: "", cropName: "", birdCount: 0 });
  error.value = "";
  zoneDialog.value = true;
}

async function saveZone() {
  const body = { ...zoneForm };
  const ok = await act(() => zoneForm.id
    ? api(`/api/zones/${zoneForm.id}`, { method: "PUT", body })
    : api("/api/zones", { method: "POST", body }), zoneForm.id ? "Zone updated" : "Zone created");
  if (ok) zoneDialog.value = false;
}

async function deleteZone(zone: Zone) {
  if (await confirm({ title: `Delete ${zone.name}?`, message: "All its devices, readings, rules and schedules are deleted too. This cannot be undone.",
    confirmLabel: "Delete zone", danger: true })) {
    act(() => api(`/api/zones/${zone.id}`, { method: "DELETE" }), "Zone deleted");
  }
}
const zoneMenu = (zone: Zone): MenuAction[] => [
  { label: "Default rules", icon: Wand2, run: () => act(() => api(`/api/zones/${zone.id}/default-rules`, { method: "POST" }), "Default rules added") },
  { label: "Edit zone", icon: Pencil, run: () => openZone(zone) },
  { label: "Delete zone", icon: Trash2, danger: true, separated: true, run: () => deleteZone(zone) },
];

// ----- device dialog (UC-2) -----
const deviceDialog = ref(false);
const deviceForm = reactive({ zoneId: 0, kind: "sensor" as "sensor" | "actuator", sensorType: "SOIL_MOISTURE" as SensorType,
  actuatorType: "WATER_PUMP" as ActuatorType, name: "", simulated: true, maxRunMinutes: 10 });

function openDevice(zone: Zone) {
  Object.assign(deviceForm, { zoneId: zone.id, kind: "sensor", name: "", simulated: true,
    maxRunMinutes: defaultRunMinutes[deviceForm.actuatorType] });
  error.value = "";
  deviceDialog.value = true;
}

// Each kind of actuator has its own sensible safety limit: a heater runs longer than a mister.
watch(() => deviceForm.actuatorType, (type) => {
  deviceForm.maxRunMinutes = defaultRunMinutes[type];
});

async function saveDevice() {
  const ok = await act(() => deviceForm.kind === "sensor"
    ? api(`/api/zones/${deviceForm.zoneId}/sensors`, { method: "POST",
        body: { type: deviceForm.sensorType, name: deviceForm.name, simulated: deviceForm.simulated } })
    : api(`/api/zones/${deviceForm.zoneId}/actuators`, { method: "POST",
        body: { type: deviceForm.actuatorType, name: deviceForm.name, maxRunMinutes: deviceForm.maxRunMinutes } }),
  "Device registered");
  if (ok) deviceDialog.value = false;
}

// ----- rename a sensor, or rename / change the safety limit of an actuator -----
const editDialog = ref(false);
const editForm = reactive({ kind: "sensor" as "sensor" | "actuator", id: 0, name: "", maxRunMinutes: 10 });

function openEdit(kind: "sensor" | "actuator", device: Sensor | Actuator) {
  Object.assign(editForm, { kind, id: device.id, name: device.name, maxRunMinutes: "maxRunMinutes" in device ? device.maxRunMinutes : 10 });
  error.value = "";
  editDialog.value = true;
}

async function saveEdit() {
  const ok = await act(() => editForm.kind === "sensor"
    ? api(`/api/sensors/${editForm.id}`, { method: "PUT", body: { name: editForm.name } })
    : api(`/api/actuators/${editForm.id}`, { method: "PUT", body: { name: editForm.name, maxRunMinutes: editForm.maxRunMinutes } }),
  "Device updated");
  if (ok) editDialog.value = false;
}

// ----- technician lifecycle (Deliverable 9) -----
function lifecycle(a: Actuator, action: LifecycleAction, body?: { testPassed?: boolean }) {
  const done = action === "COMPLETE_REPAIR" && body?.testPassed === false ? "test failed, still in maintenance" : actuatorDone[action];
  act(() => api(`/api/actuators/${a.id}/lifecycle/${action}`, { method: "POST", body: body ?? {} }), `${a.name}: ${done}`);
}

// ----- sensor repair: fault -> maintenance -> a valid reading proves it works (see utils/sensorRepair.ts) -----
async function sensorLifecycle(s: Sensor, action: SensorAction) {
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

async function removeSensor(s: Sensor) {
  if (await confirm({ title: `Remove ${s.name}?`, message: "Its readings and rules are deleted too.", confirmLabel: "Remove", danger: true })) {
    act(() => api(`/api/sensors/${s.id}`, { method: "DELETE" }), "Sensor removed");
  }
}
async function removeActuator(a: Actuator) {
  if (await confirm({ title: `Remove ${a.name}?`, confirmLabel: "Remove", danger: true })) {
    act(() => api(`/api/actuators/${a.id}`, { method: "DELETE" }), "Actuator removed");
  }
}

// One visible button per row, only when something needs doing (a fault, a repair); the rest sit in the ⋯ menu.
const abnormal = (status: string) => ["OFFLINE", "FAULT", "MAINTENANCE"].includes(status);
const optionIcon = (action: string) => (action === "FAULT" ? CircleAlert : action === "OFFLINE" ? Unplug : Wrench);
const sensorPrimary = (s: Sensor) => (abnormal(s.status) ? sensorOptions[s.status][0] ?? null : null);
const sensorMenu = (s: Sensor): MenuAction[] => [
  ...sensorOptions[s.status].filter((o) => o !== sensorPrimary(s)).map((o) => ({ label: o.label, icon: optionIcon(o.action), run: () => sensorLifecycle(s, o.action) })),
  { label: "Edit", icon: Pencil, separated: true, run: () => openEdit("sensor", s) },
  { label: "Remove", icon: Trash2, danger: true, run: () => removeSensor(s) },
];
const actuatorPrimary = (a: Actuator) => (abnormal(a.status) ? actuatorOptions[a.status]?.[0] ?? null : null);
const actuatorMenu = (a: Actuator): MenuAction[] => [
  ...(actuatorOptions[a.status] ?? []).filter((o) => o !== actuatorPrimary(a)).map((o) => ({ label: o.label, icon: optionIcon(o.action), run: () => lifecycle(a, o.action, o.body) })),
  ...(a.status !== "DECOMMISSIONED" ? [{ label: "Edit", icon: Pencil, separated: true, run: () => openEdit("actuator", a) }] : []),
  ...(a.status === "DECOMMISSIONED" ? [{ label: "Remove", icon: Trash2, danger: true, separated: true, run: () => removeActuator(a) }] : []),
];
</script>

<template>
  <div>
    <PageHeader title="Zones & devices" description="Manage farm zones (Farm Owner) and register or maintain devices (Technician).">
      <Button v-if="can()" variant="outline" @click="openFarm()"><Settings /> Farm settings</Button>
      <Button v-if="can()" @click="openZone()"><Plus /> New zone</Button>
    </PageHeader>

    <Card v-if="farm && farm.zones.length === 0" class="mb-4">
      <CardHeader>
        <CardTitle>No zones yet</CardTitle>
        <CardDescription>
          Step 1: the Farm Owner creates a zone (for example "Test bed").
          Step 2: the Technician adds devices to it. Untick "Simulated" for a real ESP32 sensor and note its #id.
        </CardDescription>
      </CardHeader>
    </Card>

    <Alert v-if="error && !(farmDialog || zoneDialog || deviceDialog || editDialog)" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <div v-if="(farm?.zones.length ?? 0) > 3" class="mb-4 flex flex-wrap items-center gap-3">
      <div class="relative min-w-[16rem] flex-1">
        <Search class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input v-model="search" class="h-10 pl-10" :placeholder="t('Find a zone or device')" :aria-label="t('Find a zone or device')" />
      </div>
      <span class="text-sm text-muted-foreground">{{ filteredZones.length }} / {{ farm?.zones.length }} {{ t("zones") }}</span>
    </div>

    <div class="stagger flex flex-col gap-4">
      <Card v-for="zone in filteredZones.slice(0, zoneLimit)" :key="zone.id">
        <CardHeader>
          <CardTitle class="flex items-center gap-2">
            {{ zone.name }}
            <span v-if="zone.lab" class="eyebrow text-primary" title="Farm Lab test zone: its alerts stay in the lab">LAB</span>
          </CardTitle>
          <CardDescription>{{ zone.type === "CROP" ? "Crop zone" : "Poultry zone" }} · {{ zone.detail }}</CardDescription>
          <CardAction class="flex items-center gap-2">
            <Button size="sm" variant="ghost" as-child><NuxtLink :to="`/zone/${zone.id}`">Open</NuxtLink></Button>
            <Button v-if="can('TECHNICIAN')" size="sm" variant="outline" @click="openDevice(zone)"><Plus /> Device</Button>
            <ActionMenu v-if="can()" :actions="zoneMenu(zone)" :label="`More actions for ${zone.name}`" />
          </CardAction>
        </CardHeader>
        <CardContent>
          <!-- Phones: one stacked card per device, so status and actions stay visible -->
          <div class="flex flex-col gap-2 md:hidden">
            <p v-if="!zone.sensors.length && !zone.actuators.length" class="py-4 text-center text-sm text-muted-foreground">No devices registered yet.</p>
            <div v-for="s in zone.sensors" :key="`ms${s.id}`" class="flex flex-col gap-2 rounded-lg border p-3">
              <div class="flex items-center gap-2">
                <span class="font-medium">{{ s.name }}</span>
                <span class="text-xs text-muted-foreground">#{{ s.id }}</span>
                <span class="ml-auto"><StatusBadge :status="sensorBadge(s)" /></span>
              </div>
              <p class="text-sm text-muted-foreground">{{ sensorKind(s) }} sensor · {{ s.simulated ? "simulated" : "real device" }}</p>
              <p v-if="sensorNote(s)" class="text-xs text-muted-foreground">{{ sensorNote(s) }}</p>
              <div v-if="can('TECHNICIAN')" class="flex items-center justify-end gap-2">
                <Button v-if="sensorPrimary(s)" size="sm" @click="sensorLifecycle(s, sensorPrimary(s)!.action)">{{ sensorPrimary(s)!.label }}</Button>
                <ActionMenu :actions="sensorMenu(s)" :label="`More actions for ${s.name}`" />
              </div>
            </div>
            <div v-for="a in zone.actuators" :key="`ma${a.id}`" class="flex flex-col gap-2 rounded-lg border p-3">
              <div class="flex items-center gap-2">
                <span class="font-medium">{{ a.name }}</span>
                <span class="text-xs text-muted-foreground">#{{ a.id }}</span>
                <span class="ml-auto"><StatusBadge :status="a.status" /></span>
              </div>
              <p class="text-sm text-muted-foreground">{{ actuatorKind(a) }} · max {{ a.maxRunMinutes }} min</p>
              <div v-if="can('TECHNICIAN')" class="flex items-center justify-end gap-2">
                <Button v-if="actuatorPrimary(a)" size="sm" @click="lifecycle(a, actuatorPrimary(a)!.action, actuatorPrimary(a)!.body)">{{ actuatorPrimary(a)!.label }}</Button>
                <ActionMenu :actions="actuatorMenu(a)" :label="`More actions for ${a.name}`" />
              </div>
            </div>
          </div>
          <Table class="hidden md:table">
            <TableHeader>
              <TableRow>
                <TableHead>Device</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead class="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="s in zone.sensors" :key="`s${s.id}`">
                <TableCell class="font-medium">{{ s.name }} <span class="text-xs text-muted-foreground">#{{ s.id }}</span></TableCell>
                <TableCell>
                  {{ sensorKind(s) }} sensor · {{ s.simulated ? "simulated" : "real device" }}
                  <div v-if="sensorNote(s)" class="text-xs text-muted-foreground">{{ sensorNote(s) }}</div>
                </TableCell>
                <TableCell><StatusBadge :status="sensorBadge(s)" /></TableCell>
                <TableCell class="text-right">
                  <div v-if="can('TECHNICIAN')" class="flex items-center justify-end gap-2">
                    <Button v-if="sensorPrimary(s)" size="sm" @click="sensorLifecycle(s, sensorPrimary(s)!.action)">{{ sensorPrimary(s)!.label }}</Button>
                    <ActionMenu :actions="sensorMenu(s)" :label="`More actions for ${s.name}`" />
                  </div>
                </TableCell>
              </TableRow>
              <TableRow v-for="a in zone.actuators" :key="`a${a.id}`">
                <TableCell class="font-medium">{{ a.name }} <span class="text-xs text-muted-foreground">#{{ a.id }}</span></TableCell>
                <TableCell>{{ actuatorKind(a) }} · max {{ a.maxRunMinutes }} min</TableCell>
                <TableCell><StatusBadge :status="a.status" /></TableCell>
                <TableCell class="text-right">
                  <div v-if="can('TECHNICIAN')" class="flex items-center justify-end gap-2">
                    <Button v-if="actuatorPrimary(a)" size="sm" @click="lifecycle(a, actuatorPrimary(a)!.action, actuatorPrimary(a)!.body)">{{ actuatorPrimary(a)!.label }}</Button>
                    <ActionMenu :actions="actuatorMenu(a)" :label="`More actions for ${a.name}`" />
                  </div>
                </TableCell>
              </TableRow>
              <TableRow v-if="!zone.sensors.length && !zone.actuators.length">
                <TableCell colspan="4" class="text-center text-muted-foreground">No devices registered yet.</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>

    <div v-if="filteredZones.length > zoneLimit" class="mt-4 flex justify-center">
      <button type="button" class="round-btn w-auto px-5 text-sm" @click="zoneLimit += 10">
        {{ t("Show more zones") }} <span class="ml-1.5 text-muted-foreground">({{ filteredZones.length - zoneLimit }} {{ t("left") }})</span>
      </button>
    </div>
    <p v-if="farm && farm.zones.length && !filteredZones.length" class="py-8 text-center text-sm text-muted-foreground">{{ t("No zone or device matches.") }}</p>

    <Dialog v-model:open="editDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ editForm.kind === "sensor" ? "Edit sensor" : "Edit actuator" }}</DialogTitle>
          <DialogDescription>
            {{ editForm.kind === "sensor" ? "Renaming keeps the #id, so the ESP32 does not need changes." : "The safety limit is the longest one run may last." }}
          </DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveEdit">
          <div class="flex flex-col gap-1.5">
            <Label for="ename">Name</Label>
            <Input id="ename" v-model="editForm.name" required maxlength="80" />
          </div>
          <div v-if="editForm.kind === 'actuator'" class="flex flex-col gap-1.5">
            <Label for="emax">Safety limit (minutes)</Label>
            <Input id="emax" v-model.number="editForm.maxRunMinutes" type="number" min="1" max="240" required />
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="farmDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Farm settings</DialogTitle>
          <DialogDescription>The name and location shown on the dashboard.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveFarm">
          <div class="flex flex-col gap-1.5">
            <Label for="farm-name">Farm name</Label>
            <Input id="farm-name" v-model="farmForm.name" required maxlength="80" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="farm-location">Location</Label>
            <Input id="farm-location" v-model="farmForm.location" maxlength="120" placeholder="e.g. Takeo, Cambodia" />
          </div>
          <label class="flex items-start gap-3 rounded-xl border p-3 text-sm" :class="{ 'opacity-60': isCompany }">
            <input type="checkbox" class="mt-0.5 size-4 accent-[var(--primary)]" :checked="farm?.supportAllowed ?? true"
              :disabled="isCompany || supportBusy" @change="setSupportAccess(($event.target as HTMLInputElement).checked)">
            <span><b class="font-medium">Allow Smart Farm support to open this farm</b><br>
              <span class="text-muted-foreground">Support staff assigned to your farm can check devices and fix problems from a distance.
                Everything they do appears in the activity log under their name.<template v-if="isCompany"> Only the farm's owner can change this.</template></span></span>
          </label>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="zoneDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ zoneForm.id ? "Edit zone" : "New zone" }}</DialogTitle>
          <DialogDescription>Zones group the sensors and actuators of one area of the farm.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveZone">
          <div v-if="!zoneForm.id" class="flex flex-col gap-1.5">
            <Label for="zone-type">Type</Label>
            <NativeSelect id="zone-type" v-model="zoneForm.type" class="w-full">
              <NativeSelectOption value="CROP">Crop zone</NativeSelectOption>
              <NativeSelectOption value="POULTRY">Poultry zone</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="zone-name">Name</Label>
            <Input id="zone-name" v-model="zoneForm.name" required maxlength="80" />
          </div>
          <div v-if="zoneForm.type === 'CROP'" class="flex flex-col gap-1.5">
            <Label for="crop">Crop</Label>
            <Input id="crop" v-model="zoneForm.cropName" placeholder="e.g. Morning glory" />
          </div>
          <div v-else class="flex flex-col gap-1.5">
            <Label for="birds">Number of birds</Label>
            <Input id="birds" v-model.number="zoneForm.birdCount" type="number" min="0" />
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="deviceDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Register device</DialogTitle>
          <DialogDescription>Add a sensor or actuator to this zone.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveDevice">
          <div class="flex flex-col gap-1.5">
            <Label for="kind">Kind</Label>
            <NativeSelect id="kind" v-model="deviceForm.kind" class="w-full">
              <NativeSelectOption value="sensor">Sensor</NativeSelectOption>
              <NativeSelectOption value="actuator">Actuator</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="dtype">Type</Label>
            <NativeSelect v-if="deviceForm.kind === 'sensor'" id="dtype" v-model="deviceForm.sensorType" class="w-full">
              <NativeSelectOption v-for="(label, key) in sensorLabels" :key="key" :value="key">{{ label }}</NativeSelectOption>
            </NativeSelect>
            <NativeSelect v-else id="dtype" v-model="deviceForm.actuatorType" class="w-full">
              <NativeSelectOption v-for="(label, key) in actuatorLabels" :key="key" :value="key">{{ label }}</NativeSelectOption>
            </NativeSelect>
            <p class="text-xs text-muted-foreground">
              {{ deviceForm.kind === "sensor" ? sensorHints[deviceForm.sensorType] : actuatorHints[deviceForm.actuatorType] }}
            </p>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="dname">Name</Label>
            <Input id="dname" v-model="deviceForm.name" required maxlength="80" placeholder="e.g. Soil moisture B2" />
          </div>
          <div v-if="deviceForm.kind === 'sensor'" class="flex flex-col gap-1.5">
            <label class="flex items-center gap-2 text-sm">
              <input v-model="deviceForm.simulated" type="checkbox" class="size-4 accent-[var(--primary)]">
              Simulated (untick for a real ESP32 sensor)
            </label>
            <p class="text-xs text-muted-foreground">
              After saving, the device shows a #id in the table. Put that id in the ESP32's secrets.h.
            </p>
          </div>
          <div v-else class="flex flex-col gap-1.5">
            <Label for="maxrun">Safety limit (max minutes per run)</Label>
            <Input id="maxrun" v-model.number="deviceForm.maxRunMinutes" type="number" min="1" max="240" />
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Register</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
