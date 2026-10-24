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

// ----- filter by zone (kept in the address, so a link or a refresh keeps it) -----
const route = useRoute();
const router = useRouter();
const zoneFilter = ref<number | "">(route.query.zone ? Number(route.query.zone) : "");
const search = ref("");
watch(zoneFilter, (z) => router.replace({ query: { ...route.query, zone: z === "" ? undefined : String(z) } }));
const zoneCounts = computed(() => {
  const counts = new Map<number, number>();
  for (const r of rules.value) counts.set(r.zoneId, (counts.get(r.zoneId) ?? 0) + 1);
  return counts;
});
const shownRules = computed(() => {
  const q = search.value.trim().toLowerCase();
  return rules.value.filter((r) => (zoneFilter.value === "" || r.zoneId === zoneFilter.value)
    && (!q || [r.name, r.sensorName, r.actuatorName ?? "", r.zoneName].some((x) => x.toLowerCase().includes(q))));
});

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
  keepRange: false, stopAt: 0,
});

/** A sensible stop value a few units back inside the comfortable side, for when "keep in range" is ticked. */
function suggestStop() {
  const span = selectedSensor.value ? (selectedSensor.value.maxValid - selectedSensor.value.minValid) * 0.05 : 3;
  const step = Math.max(1, Math.round(span));
  return form.condition === "ABOVE" ? form.threshold - step : form.threshold + step;
}
watch(() => form.keepRange, (on) => {
  if (on && (form.condition === "ABOVE" ? form.stopAt >= form.threshold : form.stopAt <= form.threshold)) form.stopAt = suggestStop();
});

// A rule may only drive an actuator in the same zone as its sensor.
const zoneActuators = computed(() => {
  const zone = farm.value?.zones.find((z) => z.sensors.some((s) => s.id === form.sensorId));
  return zone?.actuators.filter((a) => a.status !== "DECOMMISSIONED") ?? [];
});
const selectedSensor = computed(() => sensors.value.find((s) => s.id === form.sensorId));
const selectedActuator = computed(() => zoneActuators.value.find((a) => a.id === form.actuatorId));

function open(rule?: Rule) {
  const firstSensor = sensors.value.find((s) => zoneFilter.value === "" || s.zoneId === zoneFilter.value) ?? sensors.value[0];
  Object.assign(form, rule
    ? { ...rule, actuatorId: rule.actuatorId ?? "", keepRange: rule.stopAt !== null, stopAt: rule.stopAt ?? 0 }
    : { id: 0, name: "", sensorId: firstSensor?.id ?? 0, condition: "BELOW", threshold: 30, actuatorId: "",
        durationMin: 3, severity: "WARNING", cooldownMinutes: 5, keepRange: false, stopAt: 0 });
  error.value = "";
  dialog.value = true;
}

async function save() {
  error.value = "";
  const { keepRange, ...rest } = form;
  const body = { ...rest, actuatorId: form.actuatorId === "" ? null : form.actuatorId,
    durationMin: form.actuatorId === "" ? 0 : form.durationMin,
    stopAt: keepRange && form.actuatorId !== "" ? form.stopAt : null };
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

    <Alert v-if="error && !(dialog)" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div class="flex flex-col gap-1.5">
        <Label for="rzone">Zone</Label>
        <NativeSelect id="rzone" v-model="zoneFilter" class="w-60">
          <NativeSelectOption value="">All zones ({{ rules.length }})</NativeSelectOption>
          <NativeSelectOption v-for="z in farm?.zones ?? []" :key="z.id" :value="z.id">{{ z.name }} ({{ zoneCounts.get(z.id) ?? 0 }})</NativeSelectOption>
        </NativeSelect>
      </div>
      <div class="flex min-w-[12rem] flex-1 flex-col gap-1.5 sm:max-w-xs">
        <Label for="rsearch">Search</Label>
        <Input id="rsearch" v-model="search" type="search" placeholder="Rule, sensor or actuator" />
      </div>
      <p class="pb-2 text-sm text-muted-foreground">{{ shownRules.length }} of {{ rules.length }} rules</p>
    </div>

    <Card>
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Active</TableHead>
              <TableHead>Rule</TableHead>
              <TableHead>Zone</TableHead>
              <TableHead>When</TableHead>
              <TableHead>Then</TableHead>
              <TableHead>Severity</TableHead>
              <TableHead>Last fired</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="r in shownRules" :key="r.id">
              <TableCell>
                <Switch :model-value="r.active" :disabled="!can()" :aria-label="`Rule ${r.name} active`"
                  @update:model-value="(v: boolean) => toggle(r, v)" />
              </TableCell>
              <TableCell class="font-medium">{{ r.name }}</TableCell>
              <TableCell class="text-muted-foreground">{{ r.zoneName }}</TableCell>
              <TableCell>{{ r.sensorName }} {{ r.condition === "ABOVE" ? ">" : "<" }} {{ r.threshold }}{{ r.unit }}</TableCell>
              <TableCell>
                <template v-if="r.actuatorName && r.stopAt !== null">
                  Run {{ r.actuatorName }} until {{ r.stopAt }}{{ r.unit }}
                  <div class="text-xs text-muted-foreground">keeps the range · max {{ r.durationMin }} min</div>
                </template>
                <template v-else-if="r.actuatorName">Run {{ r.actuatorName }} {{ r.durationMin }} min</template>
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
            <TableRow v-if="!shownRules.length">
              <TableCell colspan="8" class="text-center text-muted-foreground">{{ rules.length ? "No rule matches this filter." : "No rules yet." }}</TableCell>
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
            <Input id="rname" v-model="form.name" required maxlength="80" />
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
            <Input id="rthr" v-model.number="form.threshold" type="number" step="0.1" required
              :min="selectedSensor?.minValid" :max="selectedSensor?.maxValid" />
            <p v-if="selectedSensor" class="text-xs text-muted-foreground">
              {{ selectedSensor.minValid }} to {{ selectedSensor.maxValid }} {{ selectedSensor.unit }}
            </p>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="ract">Actuator</Label>
            <NativeSelect id="ract" v-model="form.actuatorId" class="w-full">
              <NativeSelectOption value="">None (alert only)</NativeSelectOption>
              <NativeSelectOption v-for="a in zoneActuators" :key="a.id" :value="a.id">{{ a.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="rdur">{{ form.keepRange ? "Longest run (min)" : "Run for (min)" }}</Label>
            <Input id="rdur" v-model.number="form.durationMin" type="number" min="1" :max="selectedActuator?.maxRunMinutes"
              :disabled="form.actuatorId === ''" />
          </div>
          <div v-if="form.actuatorId !== ''" class="col-span-2 flex flex-col gap-2 rounded-lg border p-3">
            <label class="flex items-center gap-2 text-sm font-medium">
              <input v-model="form.keepRange" type="checkbox" class="size-4 accent-[var(--primary)]">
              Keep in range: stop when the reading is back to a value
            </label>
            <div v-if="form.keepRange" class="flex flex-wrap items-center gap-2 text-sm">
              <span>Stop at</span>
              <Input id="rstop" v-model.number="form.stopAt" type="number" step="0.1" class="h-8 w-24"
                :min="selectedSensor?.minValid" :max="selectedSensor?.maxValid" />
              <span class="text-muted-foreground">{{ selectedSensor?.unit }}</span>
            </div>
            <p class="text-xs text-muted-foreground">
              <template v-if="form.keepRange">
                Like a thermostat: starts {{ form.condition === "ABOVE" ? "above" : "below" }} {{ form.threshold }}{{ selectedSensor?.unit }},
                stops at {{ form.stopAt }}{{ selectedSensor?.unit }}. The minutes are only a safety limit.
                With a heater and a fan on the same sensor, each must stop before the other one starts.
              </template>
              <template v-else>Off: the actuator runs for the minutes above, then stops even if the reading is still out of range.</template>
            </p>
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
          <FormError :message="error" class="col-span-2" />
          <DialogFooter class="col-span-2"><Button type="submit">Save rule</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
