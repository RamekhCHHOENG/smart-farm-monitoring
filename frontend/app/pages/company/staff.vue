<script setup lang="ts">
import { Link2, Plus, ShieldCheck, Trash2, Wrench } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { CompanyFarm, CompanyInvite, CompanyStaff } from "~/utils/types";
import type { MenuAction } from "~/utils/menu";

// The company's own people: admins (make farms, manage staff) and support staff (look after assigned farms).
// New people get an invite link to set their own password.
definePageMeta({ companyAdmin: true });

const api = useApi();
const { user } = useAuth();
const toast = useToast();
const { confirm } = useConfirm();
const staff = ref<CompanyStaff[] | null>(null);
const farms = ref<CompanyFarm[]>([]);
async function load() {
  try {
    [staff.value, farms.value] = await Promise.all([api<CompanyStaff[]>("/api/company/staff"), api<CompanyFarm[]>("/api/company/farms")]);
  } catch (e) {
    toast.error(errorMessage(e));
  }
}
usePolling(load, 30000);
const farmsOf = (s: CompanyStaff) => farms.value.filter((f) => f.support.some((x) => x.id === s.id)).map((f) => f.name);

const open = ref(false);
const form = reactive({ name: "", username: "", companyRole: "SUPPORT" as "ADMIN" | "SUPPORT" });
const formError = ref("");
function start() {
  Object.assign(form, { name: "", username: "", companyRole: "SUPPORT" });
  formError.value = "";
  open.value = true;
}
const invite = ref<{ who: string; username: string; link: string } | null>(null);
function showInvite(res: CompanyInvite, who: string) {
  invite.value = { who, username: res.username, link: `${window.location.origin}/invite/${res.inviteToken}` };
}
async function create() {
  formError.value = "";
  try {
    const res = await api<CompanyInvite>("/api/company/staff", { method: "POST", body: { ...form } });
    open.value = false;
    showInvite(res, form.name);
    await load();
  } catch (e) {
    formError.value = errorMessage(e);
  }
}
const copied = ref(false);
async function copyLink() {
  if (!invite.value) return;
  try {
    await navigator.clipboard.writeText(invite.value.link);
    copied.value = true;
    setTimeout(() => (copied.value = false), 1500);
  } catch {
    (document.getElementById("staff-invite-link") as HTMLInputElement | null)?.select();
  }
}
const menuOf = (s: CompanyStaff): MenuAction[] => [
  { label: "New invite link", icon: Link2, run: async () => {
    try { showInvite(await api<CompanyInvite>(`/api/company/staff/${s.id}/invite`, { method: "POST" }), s.name); } catch (e) { toast.error(errorMessage(e)); }
  } },
  ...(s.id !== user.value?.id ? [{ label: "Remove", icon: Trash2, danger: true, separated: true, run: async () => {
    if (!(await confirm({ title: `Remove ${s.name}?`, message: "They are signed out and taken off every farm they support.", confirmLabel: "Remove", danger: true }))) return;
    try {
      await api(`/api/company/staff/${s.id}`, { method: "DELETE" });
      toast.success(`${s.name} removed`);
      await load();
    } catch (e) {
      toast.error(errorMessage(e));
    }
  } }] : []),
];
</script>

<template>
  <div>
    <PageHeader :title="t('Company staff')" :description="t('Smart Farm’s own people. Admins make farms and manage staff; support staff look after the farms assigned to them.')">
      <Button @click="start"><Plus /> {{ t("Add staff") }}</Button>
    </PageHeader>

    <div v-if="staff === null" class="skeleton h-40 rounded-[1.4rem]" aria-busy="true" />
    <ul v-else class="stagger flex flex-col gap-3">
      <li v-for="s in staff" :key="s.id" class="flex flex-wrap items-center gap-4 rounded-[1.4rem] border bg-card p-4 shadow-soft">
        <span class="badge-disc size-11 shrink-0"><component :is="s.companyRole === 'ADMIN' ? ShieldCheck : Wrench" class="size-5" /></span>
        <div class="min-w-[12rem] flex-1 leading-tight">
          <div class="font-medium">{{ s.name }} <span v-if="s.id === user?.id" class="text-xs text-muted-foreground">({{ t("you") }})</span></div>
          <div class="font-mono text-xs text-muted-foreground">{{ s.username }}</div>
        </div>
        <span class="rounded-full px-2.5 py-0.5 text-xs font-medium" :class="s.companyRole === 'ADMIN' ? 'bg-ink text-ink-foreground' : 'bg-primary/12 text-primary'">
          {{ s.companyRole === "ADMIN" ? t("Admin") : t("Support") }}</span>
        <div class="min-w-[12rem] text-xs text-muted-foreground">
          <template v-if="s.companyRole === 'SUPPORT'">{{ farmsOf(s).length ? farmsOf(s).join(", ") : t("No farm assigned yet") }}</template>
          <template v-else>{{ t("All farms") }}</template>
        </div>
        <ActionMenu :actions="menuOf(s)" :label="`More actions for ${s.name}`" />
      </li>
    </ul>

    <Dialog v-model:open="open">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ t("Add company staff") }}</DialogTitle>
          <DialogDescription>{{ t("They get a link to set their own password.") }}</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="create">
          <div class="flex flex-col gap-1.5"><Label for="st-name">{{ t("Name") }}</Label><Input id="st-name" v-model="form.name" required maxlength="80" /></div>
          <div class="flex flex-col gap-1.5"><Label for="st-user">{{ t("Username") }}</Label><Input id="st-user" v-model="form.username" required pattern="[a-z0-9._\-]{3,30}" title="3-30 small letters, numbers, dots or dashes" /></div>
          <fieldset class="flex flex-col gap-2">
            <legend class="mb-1 text-sm font-medium">{{ t("Role") }}</legend>
            <label class="flex items-start gap-3 rounded-xl border p-3 text-sm"><input v-model="form.companyRole" type="radio" value="SUPPORT" class="mt-0.5 accent-[var(--primary)]">
              <span><b class="font-medium">{{ t("Support") }}</b><br><span class="text-muted-foreground">{{ t("Opens assigned farms as a technician: devices, faults, repairs.") }}</span></span></label>
            <label class="flex items-start gap-3 rounded-xl border p-3 text-sm"><input v-model="form.companyRole" type="radio" value="ADMIN" class="mt-0.5 accent-[var(--primary)]">
              <span><b class="font-medium">{{ t("Admin") }}</b><br><span class="text-muted-foreground">{{ t("Makes farms, invites owners, manages staff, opens any farm as its owner.") }}</span></span></label>
          </fieldset>
          <FormError :message="formError" />
          <DialogFooter><Button type="submit"><Plus /> {{ t("Add and make invite link") }}</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!invite" @update:open="(v: boolean) => { if (!v) invite = null; }">
      <DialogContent class="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{{ t("Invite link for {who}", { who: invite?.who ?? "" }) }}</DialogTitle>
          <DialogDescription>{{ t("It works once, for 7 days. Their username is") }} <b class="font-mono">{{ invite?.username }}</b>.</DialogDescription>
        </DialogHeader>
        <div class="flex items-center gap-2">
          <input id="staff-invite-link" readonly :value="invite?.link" class="h-10 min-w-0 flex-1 rounded-xl border bg-muted px-3 font-mono text-xs" @focus="($event.target as HTMLInputElement).select()">
          <Button @click="copyLink">{{ copied ? t("Copied") : t("Copy link") }}</Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
