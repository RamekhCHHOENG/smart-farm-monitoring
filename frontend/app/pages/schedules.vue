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
import type { Farm, Task } from "~/utils/types";

const DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

const api = useApi();
const { can } = useAuth();
const tasks = ref<Task[]>([]);
const farm = ref<Farm | null>(null);
const error = ref("");

async function load() {
  try {
    [tasks.value, farm.value] = await Promise.all([api<Task[]>("/api/tasks"), api<Farm>("/api/farm")]);
  } catch (e) {
    error.value = errorMessage(e);
  }
}
onMounted(load);

const actuators = computed(() => (farm.value?.zones ?? []).flatMap((z) =>
  z.actuators.filter((a) => a.status !== "DECOMMISSIONED").map((a) => ({ ...a, zoneName: z.name }))));

const dialog = ref(false);
const editingId = ref<number | null>(null);
const form = reactive({ name: "", actuatorId: 0, runTime: "07:00", durationMin: 3, repeatDays: [...DAYS] });

function open(task?: Task) {
  editingId.value = task?.id ?? null;
  Object.assign(form, task
    ? { name: task.name, actuatorId: task.actuatorId, runTime: task.runTime.slice(0, 5), durationMin: task.durationMin, repeatDays: [...task.repeatDays] }
    : { name: "", actuatorId: actuators.value[0]?.id ?? 0, runTime: "07:00", durationMin: 3, repeatDays: [...DAYS] });
  error.value = "";
  dialog.value = true;
}

function toggleDay(day: string) {
  form.repeatDays = form.repeatDays.includes(day) ? form.repeatDays.filter((d) => d !== day) : [...form.repeatDays, day];
}

async function run(fn: () => Promise<unknown>) {
  error.value = "";
  try {
    await fn();
    await load();
    return true;
  } catch (e) {
    error.value = errorMessage(e);
    return false;
  }
}

async function save() {
  const request = editingId.value
    ? () => api(`/api/tasks/${editingId.value}`, { method: "PUT", body: form })
    : () => api("/api/tasks", { method: "POST", body: form });
  if (await run(request)) dialog.value = false;
}

async function confirmDelete(task: Task) {
  if (await useConfirm().confirm({ title: `Delete the schedule "${task.name}"?`, confirmLabel: "Delete schedule", danger: true })) run(() => api(`/api/tasks/${task.id}`, { method: "DELETE" }));
}

function days(list: string[]) {
  return list.length === 7 ? "Every day" : DAYS.filter((d) => list.includes(d)).map((d) => d.slice(0, 3)).join(", ");
}
</script>

<template>
  <div>
    <PageHeader title="Schedules"
      :description="`Recurring farm tasks such as feeding at 07:00 or watering at 17:00. Times are farm time${farm ? ` (${farm.timeZone})` : ''}.`">
      <Button v-if="can()" :disabled="!actuators.length" @click="open()"><Plus /> New schedule</Button>
    </PageHeader>

    <Alert v-if="error && !(dialog)" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <Card>
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Active</TableHead>
              <TableHead>Task</TableHead>
              <TableHead>Time</TableHead>
              <TableHead>Days</TableHead>
              <TableHead>Actuator</TableHead>
              <TableHead>Next run</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="t in tasks" :key="t.id">
              <TableCell>
                <Switch :model-value="t.active" :disabled="!can()" :aria-label="`Schedule ${t.name} active`"
                  @update:model-value="(v: boolean) => run(() => api(`/api/tasks/${t.id}/active`, { method: 'PATCH', body: { active: v } }))" />
              </TableCell>
              <TableCell class="font-medium">{{ t.name }}</TableCell>
              <TableCell class="tabular-nums">{{ t.runTime.slice(0, 5) }}</TableCell>
              <TableCell>{{ days(t.repeatDays) }}</TableCell>
              <TableCell>{{ t.actuatorName }} · {{ t.durationMin }} min <span class="text-muted-foreground">({{ t.zoneName }})</span></TableCell>
              <TableCell :class="t.nextRun ? 'font-medium' : 'text-muted-foreground'">{{ formatNextRun(t.nextRun) }}</TableCell>
              <TableCell class="text-right whitespace-nowrap">
                <Button v-if="can()" size="icon-sm" variant="ghost" :aria-label="`Edit ${t.name}`" @click="open(t)"><Pencil /></Button>
                <Button v-if="can()" size="icon-sm" variant="ghost" :aria-label="`Delete ${t.name}`"
                  @click="confirmDelete(t)"><Trash2 /></Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="!tasks.length">
              <TableCell colspan="7" class="text-center text-muted-foreground">No schedules yet.</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Dialog v-model:open="dialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ editingId ? "Edit schedule" : "New schedule" }}</DialogTitle>
          <DialogDescription>The actuator starts at this time on the selected days.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="save">
          <div class="flex flex-col gap-1.5">
            <Label for="tname">Name</Label>
            <Input id="tname" v-model="form.name" required maxlength="80" placeholder="e.g. Morning feeding" />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="tact">Actuator</Label>
            <NativeSelect id="tact" v-model.number="form.actuatorId" class="w-full">
              <NativeSelectOption v-for="a in actuators" :key="a.id" :value="a.id">{{ a.zoneName }} — {{ a.name }} (max {{ a.maxRunMinutes }} min)</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="flex flex-col gap-1.5">
              <Label for="ttime">Time</Label>
              <Input id="ttime" v-model="form.runTime" type="time" required />
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="tdur">Duration (min)</Label>
              <Input id="tdur" v-model.number="form.durationMin" type="number" min="1" required />
            </div>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label>Days</Label>
            <div class="flex flex-wrap gap-1.5">
              <Button v-for="d in DAYS" :key="d" type="button" size="xs" :variant="form.repeatDays.includes(d) ? 'default' : 'outline'"
                :aria-pressed="form.repeatDays.includes(d)" @click="toggleDay(d)">{{ d.slice(0, 3) }}</Button>
            </div>
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit" :disabled="!form.repeatDays.length">Save schedule</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
