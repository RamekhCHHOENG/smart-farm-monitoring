<script setup lang="ts">
import { KeyRound, Pencil, Plus, Trash2 } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Role, User } from "~/utils/types";
import { roleLabels } from "~/utils/types";

const api = useApi();
const { user: me, can } = useAuth();
const users = ref<User[]>([]);
const error = ref("");
const notice = ref("");

async function load() {
  try {
    users.value = await api<User[]>("/api/users");
  } catch (e) {
    error.value = errorMessage(e);
  }
}
onMounted(load);

async function act(fn: () => Promise<unknown>, done: string) {
  error.value = "";
  notice.value = "";
  try {
    await fn();
    notice.value = done;
    await load();
    return true;
  } catch (e) {
    error.value = errorMessage(e);
    return false;
  }
}

// ----- add or edit -----
const dialog = ref(false);
const form = reactive({ id: 0, username: "", name: "", role: "WORKER" as Role, password: "" });

function open(u?: User) {
  Object.assign(form, u
    ? { id: u.id, username: u.username, name: u.name, role: u.role, password: "" }
    : { id: 0, username: "", name: "", role: "WORKER", password: "" });
  error.value = "";
  dialog.value = true;
}

async function save() {
  const ok = await act(() => form.id
    ? api(`/api/users/${form.id}`, { method: "PUT", body: { name: form.name, role: form.role } })
    : api("/api/users", { method: "POST", body: { username: form.username, name: form.name, role: form.role, password: form.password } }),
  form.id ? `${form.name} updated` : `${form.name} can now sign in as "${form.username.trim().toLowerCase()}"`);
  if (ok) {
    dialog.value = false;
    if (me.value && form.id === me.value.id) me.value = { ...me.value, name: form.name, role: form.role };
  }
}

// ----- reset password -----
const resetDialog = ref(false);
const resetForm = reactive({ id: 0, name: "", password: "" });

function openReset(u: User) {
  Object.assign(resetForm, { id: u.id, name: u.name, password: "" });
  error.value = "";
  resetDialog.value = true;
}

async function saveReset() {
  const ok = await act(() => api(`/api/users/${resetForm.id}/reset-password`, { method: "POST", body: { newPassword: resetForm.password } }),
    `Password reset for ${resetForm.name}. Give them the new password in person.`);
  if (ok) resetDialog.value = false;
}

function remove(u: User) {
  if (window.confirm(`Remove ${u.name}? They will be signed out and can no longer sign in. Their past actions stay in the history.`)) {
    act(() => api(`/api/users/${u.id}`, { method: "DELETE" }), `${u.name} removed`);
  }
}
</script>

<template>
  <div>
    <PageHeader :title="t('Users')" description="Who can sign in, and what they may do. Farm Workers run devices and acknowledge alerts; Technicians also register and repair devices.">
      <Button v-if="can()" @click="open()"><Plus /> Add user</Button>
    </PageHeader>

    <Alert v-if="error && !(dialog || resetDialog)" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></Alert>
    <Alert v-if="notice" class="mb-4"><AlertDescription>{{ notice }}</AlertDescription></Alert>

    <Card>
      <CardContent class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Role</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="u in users" :key="u.id">
              <TableCell class="font-medium">{{ u.name }} <span v-if="u.id === me?.id" class="text-xs text-muted-foreground">(you)</span></TableCell>
              <TableCell class="font-mono text-sm">{{ u.username }}</TableCell>
              <TableCell>{{ t(roleLabels[u.role]) }}</TableCell>
              <TableCell class="text-right whitespace-nowrap">
                <Button size="icon-sm" variant="ghost" :aria-label="`Edit ${u.name}`" @click="open(u)"><Pencil /></Button>
                <Button size="icon-sm" variant="ghost" :aria-label="`Reset password of ${u.name}`" title="Reset password" @click="openReset(u)"><KeyRound /></Button>
                <Button v-if="u.id !== me?.id" size="icon-sm" variant="ghost" :aria-label="`Remove ${u.name}`" @click="remove(u)"><Trash2 /></Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Dialog v-model:open="dialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{{ form.id ? "Edit user" : "Add user" }}</DialogTitle>
          <DialogDescription>{{ form.id ? "Change the name or role. The username stays the same." : "Give the person their username and first password; they can change the password with the key icon." }}</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="save">
          <div class="flex flex-col gap-1.5">
            <Label for="uname">Full name</Label>
            <Input id="uname" v-model="form.name" required maxlength="80" placeholder="e.g. Srey Mom" />
          </div>
          <div v-if="!form.id" class="flex flex-col gap-1.5">
            <Label for="uuser">Username</Label>
            <Input id="uuser" v-model="form.username" required maxlength="30" autocomplete="off" placeholder="e.g. sreymom" />
            <p class="text-xs text-muted-foreground">3-30 letters, numbers, dots or dashes. No spaces.</p>
          </div>
          <div class="flex flex-col gap-1.5">
            <Label for="urole">Role</Label>
            <NativeSelect id="urole" v-model="form.role" class="w-full">
              <NativeSelectOption value="WORKER">Farm Worker</NativeSelectOption>
              <NativeSelectOption value="TECHNICIAN">Technician</NativeSelectOption>
              <NativeSelectOption value="OWNER">Farm Owner (full access)</NativeSelectOption>
            </NativeSelect>
          </div>
          <div v-if="!form.id" class="flex flex-col gap-1.5">
            <Label for="upass">First password</Label>
            <Input id="upass" v-model="form.password" type="text" required minlength="8" autocomplete="new-password" />
            <p class="text-xs text-muted-foreground">At least 8 characters.</p>
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Save</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="resetDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reset password</DialogTitle>
          <DialogDescription>For {{ resetForm.name }}, who forgot their password or was locked out. They are signed out on every device.</DialogDescription>
        </DialogHeader>
        <form class="flex flex-col gap-4" @submit.prevent="saveReset">
          <div class="flex flex-col gap-1.5">
            <Label for="rpass">New password</Label>
            <Input id="rpass" v-model="resetForm.password" type="text" required minlength="8" autocomplete="new-password" />
          </div>
          <FormError :message="error" />
          <DialogFooter><Button type="submit">Reset password</Button></DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  </div>
</template>
