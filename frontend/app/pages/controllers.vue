<script setup lang="ts">
import { Copy, FileCode, KeyRound, Pencil, Plus, Trash2 } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import type { ControllerBoard, DeviceModel, Farm } from "~/utils/types";


definePageMeta({ role: "TECHNICIAN" });
// Controller boards (ESP32 nodes) carry several devices. Each board has its own key: shown once when made,
// stored only as a hash, replaceable on its own. The firmware settings come from the board's devices.

const api = useApi();
const { can } = useAuth();
const boards = ref<ControllerBoard[]>([]);
const models = ref<DeviceModel[]>([]);
const farm = ref<Farm | null>(null);
const error = ref("");
const notice = ref("");
// results of actions show as a toast at the bottom of the screen, so they are seen wherever the person scrolled to
const toast = useToast();
watch(notice, (v) => { if (v) { toast.success(v); notice.value = ""; } });

async function load() {
  try {
    [boards.value, models.value, farm.value] = await Promise.all([
      api<ControllerBoard[]>("/api/controllers"), api<DeviceModel[]>("/api/catalog/models"), api<Farm>("/api/farm"),
    ]);
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 10000);

const boardModels = computed(() => models.value.filter((m) => m.category === "CONTROLLER" && !m.retired));
const devicesOf = (boardId: number) => (farm.value?.zones ?? []).flatMap((z) => z.devices.filter((d) => d.controllerId === boardId)
  .map((d) => ({ ...d, zoneName: z.name })));

// ----- add / edit -----
const dialog = ref(false);
const form = reactive({ id: 0, name: "", modelId: "" as number | "", notes: "" });
function open(b?: ControllerBoard) {
  Object.assign(form, b ? { id: b.id, name: b.name, modelId: b.modelId ?? "", notes: b.notes ?? "" }
    : { id: 0, name: "", modelId: boardModels.value[0]?.id ?? "", notes: "" });
  error.value = "";
  dialog.value = true;
}

// ----- key shown once, and firmware -----
const secret = ref<{ title: string; key: string | null; firmware: string } | null>(null);

async function save() {
  error.value = "";
  const body = { name: form.name, modelId: form.modelId === "" ? null : form.modelId, notes: form.notes };
  try {
    if (form.id) {
      await api(`/api/controllers/${form.id}`, { method: "PUT", body });
      notice.value = `${form.name} saved`;
    } else {
      const made = await api<{ board: ControllerBoard; key: string; firmware: string }>("/api/controllers", { method: "POST", body });
      secret.value = { title: `${made.board.name} is ready`, key: made.key, firmware: made.firmware };
    }
    dialog.value = false;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function newKey(b: ControllerBoard) {
  if (!(await useConfirm().confirm({ title: `Make a new key for ${b.name}?`, message: "The old key stops working at once; update the board's firmware.", confirmLabel: "Make new key", danger: true }))) return;
  try {
    const made = await api<{ board: ControllerBoard; key: string; firmware: string }>(`/api/controllers/${b.id}/key`, { method: "POST" });
    secret.value = { title: `New key for ${b.name}`, key: made.key, firmware: made.firmware };
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function showFirmware(b: ControllerBoard) {
  try {
    const res = await api<{ firmware: string }>(`/api/controllers/${b.id}/firmware`);
    secret.value = { title: `Firmware settings for ${b.name}`, key: null, firmware: res.firmware };
  } catch (e) {
    error.value = errorMessage(e);
  }
}

async function remove(b: ControllerBoard) {
  if (!(await useConfirm().confirm({ title: `Delete ${b.name}?`, message: "Its devices stay on the farm, without a board.", confirmLabel: "Delete board", danger: true }))) return;
  try {
    await api(`/api/controllers/${b.id}`, { method: "DELETE" });
    notice.value = `${b.name} deleted`;
    await load();
  } catch (e) {
    error.value = errorMessage(e);
  }
}

const copied = ref(false);
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1500);
  } catch {
    // clipboard blocked: the text can still be selected by hand
  }
}
</script>

<template>
  <div>
    <PageHeader title="Controller boards"
      description="The ESP32 boards on the farm. Each one has its own key and only drives the devices assigned to it; its firmware settings are generated here.">
      <Button v-if="can('TECHNICIAN')" @click="open()"><Plus /> Add board</Button>
    </PageHeader>

    <Alert v-if="error && !dialog" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <Card v-if="!boards.length" class="mb-4">
      <CardHeader>
        <CardTitle>No boards yet</CardTitle>
        <CardDescription>
          Devices can also use the farm's single device key. Add a board to give one ESP32 its own key: if it is lost or
          stolen, only its devices are exposed, and its key can be replaced on its own.
        </CardDescription>
      </CardHeader>
      <CardContent v-if="can('TECHNICIAN')"><Button @click="open()"><Plus /> Add your first board</Button></CardContent>
    </Card>

    <div class="grid-cols-1 stagger grid gap-4 lg:grid-cols-2">
      <Card v-for="b in boards" :key="b.id" class="lift">
        <CardContent class="flex flex-col gap-3">
          <div class="flex gap-4">
            <div class="w-32 shrink-0 rounded-lg bg-muted/40 p-1">
              <DevicePicture :model-id="b.modelId" :wokwi-part="b.wokwiPart" kind="actuator" :height="110" />
            </div>
            <div class="min-w-0 flex-1">
              <h2 class="text-lg font-semibold">{{ b.name }}</h2>
              <p class="text-sm text-muted-foreground">{{ b.modelName ?? "No model" }} · key …{{ b.keyHint }}</p>
              <p class="text-sm" :class="b.lastSeenAt ? 'text-primary' : 'text-muted-foreground'">
                {{ b.lastSeenAt ? `Last call ${timeAgo(b.lastSeenAt)}` : "Has not called in yet" }}
              </p>
              <p v-if="b.notes" class="text-xs text-muted-foreground">{{ b.notes }}</p>
            </div>
          </div>
          <div>
            <p class="mb-1 text-sm font-medium">{{ b.deviceCount }} device(s) on this board</p>
            <ul class="flex flex-col gap-1 text-sm">
              <li v-for="d in devicesOf(b.id)" :key="d.id" class="flex justify-between gap-2 rounded bg-muted/50 px-2 py-1">
                <span>{{ d.name }} <span class="text-muted-foreground">· {{ d.modelName }}</span></span>
                <span class="text-xs text-muted-foreground">{{ d.zoneName }}</span>
              </li>
              <li v-if="!b.deviceCount" class="text-xs text-muted-foreground">Assign devices when you add them, or from the Devices page (Edit).</li>
            </ul>
          </div>
          <div v-if="can('TECHNICIAN')" class="flex flex-wrap gap-1 border-t pt-2">
            <Button size="sm" variant="outline" @click="showFirmware(b)"><FileCode /> Firmware</Button>
            <Button size="sm" variant="outline" @click="newKey(b)"><KeyRound /> New key</Button>
            <Button size="sm" variant="ghost" @click="open(b)"><Pencil /> Edit</Button>
            <Button size="sm" variant="ghost" :aria-label="`Delete ${b.name}`" @click="remove(b)"><Trash2 /></Button>
          </div>
        </CardContent>
      </Card>
    </div>

    <Dialog v-model:open="dialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ form.id ? `Edit ${form.name}` : "Add a controller board" }}</DialogTitle>
          <DialogDescription>{{ form.id ? "Name, model and notes. The key does not change." : "Its key is shown once, right after saving." }}</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="save">
          <div class="flex flex-col gap-1.5"><Label for="bname">Name</Label><Input id="bname" v-model="form.name" required maxlength="80" placeholder="e.g. Chicken house node" /></div>
          <div class="flex flex-col gap-1.5">
            <Label for="bmodel">Board model</Label>
            <NativeSelect id="bmodel" v-model="form.modelId" class="w-full">
              <NativeSelectOption v-for="m in boardModels" :key="m.id" :value="m.id">{{ m.name }}</NativeSelectOption>
            </NativeSelect>
          </div>
          <div class="flex flex-col gap-1.5"><Label for="bnotes">Notes</Label><Input id="bnotes" v-model="form.notes" maxlength="500" placeholder="Where it is mounted, power supply…" /></div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!secret" @update:open="(v: boolean) => { if (!v) secret = null; }">
      <DialogContent class="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ secret?.title }}</DialogTitle>
          <DialogDescription>
            <template v-if="secret?.key">Copy the key now: it is not shown again (the server keeps only a hash).</template>
            <template v-else>The key is not shown here; it was shown once when the board was made or got a new key.</template>
          </DialogDescription>
        </DialogHeader>
        <div v-if="secret?.key" class="flex items-center gap-2">
          <code class="flex-1 truncate rounded bg-muted px-3 py-2 font-mono text-sm">{{ secret.key }}</code>
          <Button size="sm" variant="outline" @click="copy(secret.key)"><Copy /> {{ copied ? "Copied" : "Copy" }}</Button>
        </div>
        <div class="flex flex-col gap-1.5">
          <div class="flex items-center"><span class="text-sm font-medium">secrets.h</span>
            <Button size="xs" variant="ghost" class="ml-auto" @click="copy(secret?.firmware ?? '')"><Copy /> Copy</Button></div>
          <pre class="max-h-80 overflow-auto rounded-md bg-zinc-950 p-3 font-mono text-xs leading-relaxed text-emerald-200">{{ secret?.firmware }}</pre>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
