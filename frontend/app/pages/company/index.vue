<script setup lang="ts">
import { ArrowRight, BellRing, Building2, Gauge, Link2, MapPin, Pause, Pencil, Play, Plus, Search, UserCog, Wifi } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { CompanyFarm, CompanyInvite, CompanyStaff } from "~/utils/types";
import type { MenuAction } from "~/utils/menu";

// The Smart Farm company's home: every customer's farm, how healthy it is, and the work around it
// (make a farm and invite its owner, assign support staff, suspend). "Open farm" works on that farm as its owner
// (admin) or technician (support).

const api = useApi();
const { user, setActiveFarm } = useAuth();
const toast = useToast();
const { confirm } = useConfirm();
const isAdmin = computed(() => user.value?.companyRole === "ADMIN");

const farms = ref<CompanyFarm[] | null>(null);
const staff = ref<CompanyStaff[]>([]);
const error = ref("");
async function load() {
  try {
    farms.value = await api<CompanyFarm[]>("/api/company/farms");
    error.value = "";
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 15000);
onMounted(async () => {
  if (isAdmin.value) staff.value = await api<CompanyStaff[]>("/api/company/staff").catch(() => []);
});

// ----- health -----
const HOUR = 3600000;
function attention(f: CompanyFarm) {
  if (f.status === "SUSPENDED") return null;
  if (f.zones === 0) return "Not set up yet";
  if (f.sensors === 0) return "No sensors installed yet";
  if (!f.lastReadingAt) return "No data received yet";
  if (f.lastReadingAt && Date.now() - Date.parse(f.lastReadingAt) > HOUR) return "No data for over an hour";
  if (f.sensorsOnline < f.sensors) return `${f.sensors - f.sensorsOnline} sensor${f.sensors - f.sensorsOnline > 1 ? "s" : ""} offline`;
  if (f.openAlerts > 0) return `${f.openAlerts} open alert${f.openAlerts > 1 ? "s" : ""}`;
  return null;
}
const figures = computed(() => {
  const list = farms.value ?? [];
  const active = list.filter((f) => f.status === "ACTIVE");
  return {
    farms: list.length,
    suspended: list.length - active.length,
    attention: active.filter((f) => attention(f)).length,
    online: active.reduce((s, f) => s + f.sensorsOnline, 0),
    sensors: active.reduce((s, f) => s + f.sensors, 0),
    alerts: active.reduce((s, f) => s + f.openAlerts, 0),
  };
});

// ----- search and filter -----
const search = ref("");
const filter = ref<"all" | "attention" | "suspended">("all");
const shown = computed(() => {
  const words = search.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return (farms.value ?? [])
    .filter((f) => filter.value === "all" || (filter.value === "attention" ? !!attention(f) : f.status === "SUSPENDED"))
    .filter((f) => words.every((w) => `${f.name} ${f.location} ${f.owners.join(" ")} ${f.ownerContact ?? ""}`.toLowerCase().includes(w)))
    .sort((a, b) => Number(!!attention(b)) - Number(!!attention(a)) || a.name.localeCompare(b.name));
});

// ----- open a farm -----
function openFarm(f: CompanyFarm) {
  setActiveFarm({ id: f.id, name: f.name });
  window.location.assign("/"); // a fresh start, so nothing from another farm stays on screen
}

// ----- new farm -----
const newOpen = ref(false);
const form = reactive({ name: "", location: "", ownerName: "", ownerUsername: "", ownerContact: "" });
const formError = ref("");
const usernameTouched = ref(false);
watch(() => form.ownerName, (n) => {
  if (!usernameTouched.value) form.ownerUsername = n.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, ".").replace(/^\.|\.$/g, "").slice(0, 30);
});
function startNew() {
  Object.assign(form, { name: "", location: "", ownerName: "", ownerUsername: "", ownerContact: "" });
  usernameTouched.value = false;
  formError.value = "";
  newOpen.value = true;
}
async function createFarm() {
  formError.value = "";
  try {
    const res = await api<CompanyInvite>("/api/company/farms", { method: "POST", body: { ...form } });
    newOpen.value = false;
    await load();
    showInvite(res, `${res.farm?.name} is ready`, form.ownerName);
  } catch (e) {
    formError.value = errorMessage(e);
  }
}

// ----- invite link (shown once) -----
const invite = ref<{ title: string; who: string; username: string; link: string } | null>(null);
function showInvite(res: CompanyInvite, title: string, who: string) {
  invite.value = { title, who, username: res.username, link: `${window.location.origin}/invite/${res.inviteToken}` };
}
const copied = ref(false);
async function copyLink() {
  if (!invite.value) return;
  try {
    await navigator.clipboard.writeText(invite.value.link);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1500);
  } catch {
    (document.getElementById("invite-link") as HTMLInputElement | null)?.select();
  }
}
async function newOwnerLink(f: CompanyFarm) {
  try {
    showInvite(await api<CompanyInvite>(`/api/company/farms/${f.id}/invite`, { method: "POST" }), `New link for ${f.name}`, f.owners[0] ?? "the owner");
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

// ----- edit, support, suspend -----
const editOpen = ref(false);
const edit = reactive({ id: 0, name: "", location: "", ownerContact: "" });
function startEdit(f: CompanyFarm) {
  Object.assign(edit, { id: f.id, name: f.name, location: f.location, ownerContact: f.ownerContact ?? "" });
  formError.value = "";
  editOpen.value = true;
}
async function saveEdit() {
  try {
    await api(`/api/company/farms/${edit.id}`, { method: "PUT", body: { name: edit.name, location: edit.location, ownerContact: edit.ownerContact } });
    editOpen.value = false;
    toast.success(`${edit.name} saved`);
    await load();
  } catch (e) {
    formError.value = errorMessage(e);
  }
}
const supportOpen = ref(false);
const supportFarm = ref<CompanyFarm | null>(null);
const supportPick = ref<number[]>([]);
const supportStaff = computed(() => staff.value.filter((s) => s.companyRole === "SUPPORT"));
function startSupport(f: CompanyFarm) {
  supportFarm.value = f;
  supportPick.value = f.support.map((s) => s.id);
  supportOpen.value = true;
}
async function saveSupport() {
  if (!supportFarm.value) return;
  try {
    await api(`/api/company/farms/${supportFarm.value.id}/support`, { method: "PUT", body: { staffIds: supportPick.value } });
    supportOpen.value = false;
    toast.success(`Support for ${supportFarm.value.name} saved`);
    await load();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}
async function setStatus(f: CompanyFarm, status: "ACTIVE" | "SUSPENDED") {
  if (status === "SUSPENDED" && !(await confirm({ title: `Suspend ${f.name}?`, danger: true, confirmLabel: "Suspend farm",
    message: "Its people are signed out and cannot use the farm until you reactivate it. Its data is kept, and machines keep following their rules." }))) return;
  try {
    await api(`/api/company/farms/${f.id}/status`, { method: "PUT", body: { status } });
    toast.success(status === "SUSPENDED" ? `${f.name} is suspended` : `${f.name} is active again`);
    await load();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}
const menuOf = (f: CompanyFarm): MenuAction[] => isAdmin.value ? [
  { label: "Edit details", icon: Pencil, run: () => startEdit(f) },
  { label: "New owner invite link", icon: Link2, run: () => newOwnerLink(f) },
  { label: "Assign support staff", icon: UserCog, run: () => startSupport(f) },
  f.status === "ACTIVE"
    ? { label: "Suspend farm", icon: Pause, danger: true, separated: true, run: () => setStatus(f, "SUSPENDED") }
    : { label: "Reactivate farm", icon: Play, separated: true, run: () => setStatus(f, "ACTIVE") },
] : [];
</script>

<template>
  <div>
    <PageHeader :title="t('Customer farms')"
      :description="isAdmin ? t('Every farm Smart Farm set up: its health, its owner and who supports it. Open a farm to work on it.')
        : t('The farms you support. Open one to check its devices; its owner can switch your access off.')">
      <Button v-if="isAdmin" @click="startNew"><Plus /> {{ t("New farm") }}</Button>
    </PageHeader>

    <Alert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>

    <div v-if="farms === null" class="grid grid-cols-2 gap-4 xl:grid-cols-4" aria-busy="true">
      <div v-for="i in 4" :key="i" class="skeleton h-32 rounded-[1.4rem]" />
    </div>
    <template v-else>
      <div class="stagger mb-6 grid grid-cols-2 gap-4 xl:grid-cols-4">
        <StatCard hero :label="t('Farms')" :value="figures.farms" :icon="Building2"
          :note="figures.suspended ? `${figures.suspended} suspended` : 'all active'" />
        <StatCard :label="t('Need attention')" :value="figures.attention" :icon="Gauge" :tone="figures.attention ? 'warning' : ''"
          note="offline sensors, no data or open alerts" />
        <StatCard :label="t('Sensors online')" :value="figures.online" :of="`/${figures.sensors}`" :icon="Wifi"
          :tone="figures.online < figures.sensors ? 'warning' : ''" note="across active farms" />
        <StatCard :label="t('Open alerts')" :value="figures.alerts" :icon="BellRing" :tone="figures.alerts ? 'destructive' : ''" note="across active farms" />
      </div>

      <div class="mb-4 flex flex-wrap items-center gap-3">
        <div class="relative min-w-[16rem] flex-1">
          <Search class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input v-model="search" class="h-10 pl-10" :placeholder="t('Find a farm, place or owner')" :aria-label="t('Find a farm, place or owner')" />
        </div>
        <div class="flex rounded-full border bg-card p-1" role="group" :aria-label="t('Show')">
          <button v-for="f in ([['all', 'All'], ['attention', 'Need attention'], ['suspended', 'Suspended']] as const)" :key="f[0]" type="button"
            class="rounded-full px-3.5 py-1.5 text-sm transition-colors" :class="filter === f[0] ? 'bg-ink text-ink-foreground' : 'text-muted-foreground hover:text-foreground'"
            @click="filter = f[0]">{{ t(f[1]) }}</button>
        </div>
      </div>

      <div v-if="!farms.length" class="flex flex-col items-center gap-3 rounded-[1.75rem] border border-dashed p-10 text-center">
        <Building2 class="size-8 text-muted-foreground" />
        <p class="max-w-md text-muted-foreground">{{ isAdmin ? t("No farms yet. Make the first one for a customer: you will get a link to send to its owner.")
          : t("No farm is assigned to you yet. Ask a company admin.") }}</p>
        <Button v-if="isAdmin" @click="startNew"><Plus /> {{ t("New farm") }}</Button>
      </div>

      <ul class="stagger flex flex-col gap-3">
        <li v-for="f in shown" :key="f.id" class="lift flex flex-wrap items-center gap-x-5 gap-y-3 rounded-[1.4rem] border bg-card p-4 shadow-soft"
          :class="{ 'opacity-70': f.status === 'SUSPENDED' }">
          <div class="flex min-w-[14rem] flex-1 items-start gap-3">
            <span class="badge-disc size-11 shrink-0"><Building2 class="size-5" /></span>
            <div class="min-w-0 leading-tight">
              <div class="flex flex-wrap items-center gap-2">
                <span class="text-lg font-medium">{{ f.name }}</span>
                <span v-if="f.status === 'SUSPENDED'" class="rounded-full bg-warning/18 px-2 py-0.5 text-[11px] font-medium">{{ t("Suspended") }}</span>
              </div>
              <div class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
                <span v-if="f.location" class="inline-flex items-center gap-1"><MapPin class="size-3" />{{ f.location }}</span>
                <span>{{ t("Owner") }}: {{ f.owners.join(", ") || "–" }}<template v-if="f.ownerContact"> · {{ f.ownerContact }}</template></span>
              </div>
            </div>
          </div>
          <dl class="grid grid-cols-3 gap-4 text-center text-xs text-muted-foreground sm:w-80">
            <div><dd class="num text-lg text-foreground" :class="{ 'text-warning': f.sensorsOnline < f.sensors }">{{ f.sensorsOnline }}<span class="text-sm text-muted-foreground">/{{ f.sensors }}</span></dd><dt>{{ t("sensors online") }}</dt></div>
            <div><dd class="num text-lg" :class="f.openAlerts ? 'text-destructive' : 'text-foreground'">{{ f.openAlerts }}</dd><dt>{{ t("open alerts") }}</dt></div>
            <div><dd class="num text-lg text-foreground">{{ f.lastReadingAt ? timeAgo(f.lastReadingAt) : "–" }}</dd><dt>{{ t("last data") }}</dt></div>
          </dl>
          <div class="flex min-w-[10rem] flex-col gap-1 text-xs">
            <span v-if="attention(f)" class="inline-flex w-fit items-center gap-1 rounded-full bg-warning/18 px-2.5 py-0.5 font-medium">{{ t(attention(f)!) }}</span>
            <span v-else-if="f.status === 'ACTIVE'" class="inline-flex w-fit items-center gap-1 rounded-full bg-primary/12 px-2.5 py-0.5 font-medium text-primary">{{ t("Healthy") }}</span>
            <span class="text-muted-foreground">{{ t("Support") }}: {{ f.support.map((s) => s.name).join(", ") || t("none") }}<template v-if="!f.supportAllowed"> · {{ t("owner switched access off") }}</template></span>
          </div>
          <div class="ml-auto flex items-center gap-2">
            <Button size="sm" @click="openFarm(f)">{{ t("Open farm") }} <ArrowRight /></Button>
            <ActionMenu :actions="menuOf(f)" :label="`More actions for ${f.name}`" />
          </div>
        </li>
      </ul>
      <p v-if="farms.length && !shown.length" class="py-8 text-center text-sm text-muted-foreground">{{ t("No farm matches.") }}</p>
    </template>

    <!-- new farm -->
    <Dialog v-model:open="newOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t("New farm for a customer") }}</DialogTitle>
          <DialogDescription>{{ t("The farm starts empty. Its owner gets a link to set their own password; then you or they add zones and devices.") }}</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="createFarm">
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="flex flex-col gap-1.5"><Label for="nf-name">{{ t("Farm name") }}</Label><Input id="nf-name" v-model="form.name" required maxlength="80" placeholder="e.g. Kampot Pepper Farm" /></div>
            <div class="flex flex-col gap-1.5"><Label for="nf-loc">{{ t("Location") }}</Label><Input id="nf-loc" v-model="form.location" maxlength="120" placeholder="e.g. Kampot" /></div>
          </div>
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="flex flex-col gap-1.5"><Label for="nf-owner">{{ t("Owner's name") }}</Label><Input id="nf-owner" v-model="form.ownerName" required maxlength="80" placeholder="e.g. Srey Mom" /></div>
            <div class="flex flex-col gap-1.5"><Label for="nf-user">{{ t("Owner's username") }}</Label>
              <Input id="nf-user" v-model="form.ownerUsername" required pattern="[a-z0-9._\-]{3,30}" title="3-30 small letters, numbers, dots or dashes" @input="usernameTouched = true" /></div>
          </div>
          <div class="flex flex-col gap-1.5"><Label for="nf-contact">{{ t("Owner's phone or Telegram") }}</Label><Input id="nf-contact" v-model="form.ownerContact" maxlength="80" placeholder="e.g. 012 345 678" /></div>
          <FormError :message="formError" />
          <DialogFooter><Button type="submit"><Plus /> {{ t("Create farm and invite link") }}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <!-- invite link, shown once -->
    <Dialog :open="!!invite" @update:open="(v: boolean) => { if (!v) invite = null; }">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ invite?.title }}</DialogTitle>
          <DialogDescription>{{ t("Send this link to {who} by Telegram or SMS. It works once, for 7 days. Their username is", { who: invite?.who ?? "" }) }} <b class="font-mono">{{ invite?.username }}</b>.</DialogDescription>
        </DialogHeader>
        <div class="flex items-center gap-2">
          <input id="invite-link" readonly :value="invite?.link" class="h-10 min-w-0 flex-1 rounded-xl border bg-muted px-3 font-mono text-xs" @focus="($event.target as HTMLInputElement).select()">
          <Button @click="copyLink">{{ copied ? t("Copied") : t("Copy link") }}</Button>
        </div>
        <p class="text-xs text-muted-foreground">{{ t("The link is not shown again. If it is lost, make a new one from the farm's ⋯ menu.") }}</p>
      </DialogContent>
    </Dialog>

    <!-- edit details -->
    <Dialog v-model:open="editOpen">
      <DialogContent>
        <DialogHeader><DialogTitle>{{ t("Farm details") }}</DialogTitle></DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveEdit">
          <div class="flex flex-col gap-1.5"><Label for="ef-name">{{ t("Farm name") }}</Label><Input id="ef-name" v-model="edit.name" required maxlength="80" /></div>
          <div class="flex flex-col gap-1.5"><Label for="ef-loc">{{ t("Location") }}</Label><Input id="ef-loc" v-model="edit.location" maxlength="120" /></div>
          <div class="flex flex-col gap-1.5"><Label for="ef-contact">{{ t("Owner's phone or Telegram") }}</Label><Input id="ef-contact" v-model="edit.ownerContact" maxlength="80" /></div>
          <FormError :message="formError" />
          <DialogFooter><Button type="submit">{{ t("Save") }}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <!-- assign support -->
    <Dialog v-model:open="supportOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t("Support for {farm}", { farm: supportFarm?.name ?? "" }) }}</DialogTitle>
          <DialogDescription>{{ t("Chosen staff can open this farm as a technician, while its owner allows support access.") }}</DialogDescription>
        </DialogHeader>
        <div class="flex flex-col gap-2">
          <label v-for="s in supportStaff" :key="s.id" class="flex items-center gap-3 rounded-xl border px-3 py-2.5 text-sm">
            <input v-model="supportPick" type="checkbox" :value="s.id" class="size-4 accent-[var(--primary)]">
            <span class="flex-1">{{ s.name }} <span class="font-mono text-xs text-muted-foreground">{{ s.username }}</span></span>
          </label>
          <p v-if="!supportStaff.length" class="text-sm text-muted-foreground">{{ t("No support staff yet. Add them on the Company staff page.") }}</p>
        </div>
        <DialogFooter><Button @click="saveSupport">{{ t("Save") }}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
