<script setup lang="ts">
import { Pencil, Plus, Trash2 } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Condition, Farm, Rule, Severity } from "~/utils/types";

const api = useApi();
const { can } = useAuth();
const rules = ref<Rule[]>([]);
const farm = ref<Farm | null>(null);
const error = ref("");

async function load() {
  try {
    [rules.value, farm.value] = await Promise.all([api<Rule[]>("/api/rules"), api<Farm>("/api/farm")]);
  } catch (e) {
    error.value = errorMessage(e);
  }
}
onMounted(load);

const sensors = computed(() => (farm.value?.zones ?? []).flatMap((z) => z.sensors.map((s) => ({ ...s, zoneName: z.name }))));

const dialog = ref(false);
const form = reactive({
  id: 0, name: "", sensorId: 0, condition: "BELOW" as Condition, threshold: 30,
  actuatorId: "" as number | "", durationMin: 3, severity: "WARNING" as Severity, cooldownMinutes: 5,
});

// A rule may only drive an actuator in the same zone as its sensor.
const zoneActuators = computed(() => {
  const zone = farm.value?.zones.find((z) => z.sensors.some((s) => s.id === form.sensorId));
  return zone?.actuators.filter((a) => a.status !== "DECOMMISSIONED") ?? [];
});
const selectedActuator = computed(() => zoneActuators.value.find((a) => a.id === form.actuatorId));

function open(rule?: Rule) {
  Object.assign(form, rule
    ? { ...rule, actuatorId: rule.actuatorId ?? "" }
    : { id: 0, name: "", sensorId: sensors.value[0]?.id ?? 0, condition: "BELOW", threshold: 30, actuatorId: "",
        durationMin: 3, severity: "WARNING", cooldownMinutes: 5 });
  dialog.value = true;
}

async function save() {
  error.value = "";
  const body = { ...form, actuatorId: form.actuatorId === "" ? null : form.actuatorId,
    durationMin: form.actuatorId === "" ? 0 : form.durationMin };
  try {
    await (form.id ? api(`/api/rules/${form.id}`, { method: "PUT", body }) : api("/api/rules", { method: "POST", body }));
    dialog.value = false;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function toggle(rule: Rule, active: boolean) {
  try {
    await api(`/api/rules/${rule.id}/active`, { method: "PATCH", body: { active } });
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function remove(rule: Rule) {
  if (!window.confirm(`Delete rule "${rule.name}"?`)) return;
  try {
    await api(`/api/rules/${rule.id}`, { method: "DELETE" });
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}
</script>

<template>
  <div>
    <PageHeader title="Automation rules"
      description="When a reading crosses a threshold, the system starts the actuator and alerts staff. Only the Farm Owner can change rules.">
      <Button v-if="can()" :disabled="!sensors.length" @click="open()"><Plus /> New rule</Button>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <Card>
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Active</TableHead>
              <TableHead>Rule</TableHead>
              <TableHead>When</TableHead>
              <TableHead>Then</TableHead>
              <TableHead>Severity</TableHead>
              <TableHead>Last fired</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="r in rules" :key="r.id">
              <TableCell>
                <Switch :model-value="r.active" :disabled="!can()" :aria-label="`Rule ${r.name} active`"
                  @update:model-value="(v: boolean) => toggle(r, v)" />
              </TableCell>
              <TableCell class="font-medium">{{ r.name }}</TableCell>
              <TableCell>{{ r.sensorName }} {{ r.condition === "ABOVE" ? ">" : "<" }} {{ r.threshold }}{{ r.unit }}</TableCell>
              <TableCell>
                <template v-if="r.actuatorName">Run {{ r.actuatorName }} {{ r.durationMin }} min</template>
                <span v-else class="text-muted-foreground">Alert only</span>
              </TableCell>
              <TableCell><StatusBadge :status="r.severity" /></TableCell>
              <TableCell class="text-muted-foreground">{{ timeAgo(r.lastTriggeredAt) }}</TableCell>
              <TableCell class="text-right">
                <template v-if="can()">
                  <Button size="icon-sm" variant="ghost" :aria-label="`Edit ${r.name}`" @click="open(r)"><Pencil /></Button>
                  <Button size="icon-sm" variant="ghost" :aria-label="`Delete ${r.name}`" @click="remove(r)"><Trash2 /></Button>
                </template>
              </TableCell>
            </TableRow>
            <TableRow v-if="!rules.length">
              <TableCell colspan="7" class="text-center text-muted-foreground">No rules yet.</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Dialog v-model:open="dialog">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ form.id ? "Edit rule" : "New rule" }}</DialogTitle>
          <DialogDescription>IF sensor reading crosses the threshold THEN run the actuator and send an alert.</DialogDescription>
        </DialogHeader>
        <form class="grid grid-cols-2 gap-4" @submit.prevent="save">
          <div class="col-span-2 flex flex-col gap-1.5">
            <Label for="rname">Name</Label>
            <Input id="rname" v-model="form.name" required />
          </div>
          <div class="col-span-2 flex flex-col gap-1.5">
            <Label for="rsensor">Sensor</Label>
            <NativeSelect id="rsensor" v-model.number="form.sensorId" class="w-full" :disabled="!!form.id">
              <NativeSelectOption v-for="s in sensors" :key="s.id" :value="s.id">{{ s.zoneName }} — {{ s.name }} ({{ s.unit }})</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rcond">Condition</Label>
            <NativeSelect id="rcond" v-model="form.condition" class="w-full">
              <NativeSelectOption value="BELOW">Below</NativeSelectOption>
              <NativeSelectOption value="ABOVE">Above</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rthr">Threshold</Label>
            <Input id="rthr" v-model.number="form.threshold" type="number" step="0.1" required />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="ract">Actuator</Label>
            <NativeSelect id="ract" v-model="form.actuatorId" class="w-full">
              <NativeSelectOption value="">None (alert only)</NativeSelectOption>
              <NativeSelectOption v-for="a in zoneActuators" :key="a.id" :value="a.id">{{ a.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rdur">Run for (min)</Label>
            <Input id="rdur" v-model.number="form.durationMin" type="number" min="1" :max="selectedActuator?.maxRunMinutes"
              :disabled="form.actuatorId === ''" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rsev">Severity</Label>
            <NativeSelect id="rsev" v-model="form.severity" class="w-full">
              <NativeSelectOption value="INFO">Info</NativeSelectOption>
              <NativeSelectOption value="WARNING">Warning</NativeSelectOption>
              <NativeSelectOption value="CRITICAL">Critical</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rcool">Cooldown (min)</Label>
            <Input id="rcool" v-model.number="form.cooldownMinutes" type="number" min="1" />
          </div>
          <DialogFooter class="col-span-2"><Button type="submit">Save rule</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
