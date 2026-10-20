<script setup lang="ts">
import { Plus, Settings, Trash2, Wand2 } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Actuator, ActuatorType, Farm, LifecycleAction, SensorType, Zone } from "~/utils/types";
import { actuatorLabels, sensorLabels } from "~/utils/types";

const api = useApi();
const { can } = useAuth();
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
onMounted(load);

const ask = (message: string) => window.confirm(message);

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

// ----- farm settings -----
const farmDialog = ref(false);
const farmForm = reactive({ name: "", location: "" });

function openFarm() {
  Object.assign(farmForm, { name: farm.value?.name ?? "", location: farm.value?.location ?? "" });
  farmDialog.value = true;
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
  zoneDialog.value = true;
}

async function saveZone() {
  const body = { ...zoneForm };
  const ok = await act(() => zoneForm.id
    ? api(`/api/zones/${zoneForm.id}`, { method: "PUT", body })
    : api("/api/zones", { method: "POST", body }), zoneForm.id ? "Zone updated" : "Zone created");
  if (ok) zoneDialog.value = false;
}

function deleteZone(zone: Zone) {
  if (ask(`Delete "${zone.name}" with all its devices, readings, rules and schedules?`)) {
    act(() => api(`/api/zones/${zone.id}`, { method: "DELETE" }), "Zone deleted");
  }
}

// ----- device dialog (UC-2) -----
const deviceDialog = ref(false);
const deviceForm = reactive({ zoneId: 0, kind: "sensor" as "sensor" | "actuator", sensorType: "SOIL_MOISTURE" as SensorType,
  actuatorType: "WATER_PUMP" as ActuatorType, name: "", simulated: true, maxRunMinutes: 10 });

function openDevice(zone: Zone) {
  Object.assign(deviceForm, { zoneId: zone.id, kind: "sensor", name: "", simulated: true, maxRunMinutes: 10 });
  deviceDialog.value = true;
}

async function saveDevice() {
  const ok = await act(() => deviceForm.kind === "sensor"
    ? api(`/api/zones/${deviceForm.zoneId}/sensors`, { method: "POST",
        body: { type: deviceForm.sensorType, name: deviceForm.name, simulated: deviceForm.simulated } })
    : api(`/api/zones/${deviceForm.zoneId}/actuators`, { method: "POST",
        body: { type: deviceForm.actuatorType, name: deviceForm.name, maxRunMinutes: deviceForm.maxRunMinutes } }),
  "Device registered");
  if (ok) deviceDialog.value = false;
}

// ----- technician lifecycle (Deliverable 9) -----
const lifecycleOptions: Record<string, { action: LifecycleAction; label: string; body?: object }[]> = {
  IDLE: [{ action: "FAULT", label: "Report fault" }, { action: "OFFLINE", label: "Mark offline" }],
  RUNNING: [{ action: "FAULT", label: "Report fault" }, { action: "OFFLINE", label: "Mark offline" }],
  OFFLINE: [{ action: "RESTORE", label: "Heartbeat restored" }, { action: "START_REPAIR", label: "Start repair" }],
  FAULT: [{ action: "START_REPAIR", label: "Start repair" }],
  MAINTENANCE: [
    { action: "COMPLETE_REPAIR", label: "Repair done (test passed)", body: { testPassed: true } },
    { action: "COMPLETE_REPAIR", label: "Test failed", body: { testPassed: false } },
    { action: "DECOMMISSION", label: "Decommission" },
  ],
};

function lifecycle(a: Actuator, action: LifecycleAction, body?: object) {
  act(() => api(`/api/actuators/${a.id}/lifecycle/${action}`, { method: "POST", body: body ?? {} }), `${a.name}: ${action.replace("_", " ").toLowerCase()}`);
}
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

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="notice" class="mb-4"><AlertDescription>{{ notice }}</AlertDescription></Alert>

    <div class="flex flex-col gap-4">
      <Card v-for="zone in farm?.zones ?? []" :key="zone.id">
        <CardHeader>
          <CardTitle>{{ zone.name }}</CardTitle>
          <CardDescription>{{ zone.type === "CROP" ? "Crop zone" : "Poultry zone" }} · {{ zone.detail }}</CardDescription>
          <CardAction class="flex flex-wrap gap-2">
            <Button v-if="can('TECHNICIAN')" size="sm" variant="outline" @click="openDevice(zone)"><Plus /> Device</Button>
            <Button v-if="can()" size="sm" variant="outline" title="Create the default rules this zone type proposes"
              @click="act(() => api(`/api/zones/${zone.id}/default-rules`, { method: 'POST' }), 'Default rules added')">
              <Wand2 /> Default rules
            </Button>
            <Button v-if="can()" size="sm" variant="outline" @click="openZone(zone)">Edit</Button>
            <Button v-if="can()" size="icon-sm" variant="ghost" :aria-label="`Delete ${zone.name}`" @click="deleteZone(zone)"><Trash2 /></Button>
          </CardAction>
        </CardHeader>
        <CardContent class="overflow-x-auto">
          <Table>
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
                <TableCell>{{ sensorLabels[s.type] }} sensor · {{ s.simulated ? "simulated" : "real device" }}</TableCell>
                <TableCell><StatusBadge :status="s.online ? (s.suspect ? 'WARNING' : 'ONLINE') : 'OFFLINE'" /></TableCell>
                <TableCell class="text-right">
                  <Button v-if="can('TECHNICIAN')" size="sm" variant="ghost"
                    @click="ask(`Remove ${s.name}?`) && act(() => api(`/api/sensors/${s.id}`, { method: 'DELETE' }), 'Sensor removed')">Remove</Button>
                </TableCell>
              </TableRow>
              <TableRow v-for="a in zone.actuators" :key="`a${a.id}`">
                <TableCell class="font-medium">{{ a.name }} <span class="text-xs text-muted-foreground">#{{ a.id }}</span></TableCell>
                <TableCell>{{ actuatorLabels[a.type] }} · max {{ a.maxRunMinutes }} min</TableCell>
                <TableCell><StatusBadge :status="a.status" /></TableCell>
                <TableCell class="text-right">
                  <div v-if="can('TECHNICIAN')" class="flex flex-wrap justify-end gap-1">
                    <Button v-for="o in lifecycleOptions[a.status] ?? []" :key="o.label" size="sm" variant="outline"
                      @click="lifecycle(a, o.action, o.body)">{{ o.label }}</Button>
                    <Button v-if="a.status === 'DECOMMISSIONED'" size="sm" variant="ghost"
                      @click="act(() => api(`/api/actuators/${a.id}`, { method: 'DELETE' }), 'Actuator removed')">Remove</Button>
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
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="dname">Name</Label>
            <Input id="dname" v-model="deviceForm.name" required placeholder="e.g. Soil moisture B2" />
          </div>
          <label v-if="deviceForm.kind === 'sensor'" class="flex items-center gap-2 text-sm">
            <input v-model="deviceForm.simulated" type="checkbox" class="size-4 accent-[var(--primary)]">
            Simulated (untick for a real ESP32 sensor)
          </label>
          <p class="text-xs text-muted-foreground">
            After saving, the device shows a #id in the table. Put that id in the ESP32's secrets.h.
          </p>
          <div v-else class="flex flex-col gap-1.5">
            <Label for="maxrun">Safety limit (max minutes per run)</Label>
            <Input id="maxrun" v-model.number="deviceForm.maxRunMinutes" type="number" min="1" max="240" />
          </div>
          <DialogFooter><Button type="submit">Register</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
