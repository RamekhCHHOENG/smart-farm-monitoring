<script setup lang="ts">
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

const open = defineModel<boolean>("open", { default: false });
const api = useApi();
const form = reactive({ current: "", next: "", repeat: "" });
const error = ref("");
const done = ref(false);

watch(open, (value) => {
  if (value) {
    Object.assign(form, { current: "", next: "", repeat: "" });
    error.value = "";
    done.value = false;
  }
});

async function save() {
  error.value = "";
  if (form.next !== form.repeat) {
    error.value = "The new passwords do not match";
    return;
  }
  try {
    await api("/api/auth/password", { method: "POST", body: { currentPassword: form.current, newPassword: form.next } });
    done.value = true;
  } catch (e) {
    error.value = errorMessage(e);
  }
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Change password</DialogTitle>
        <DialogDescription>Use at least 8 characters. Do this after the first login with the generated password.</DialogDescription>
      </DialogHeader>
      <p v-if="done" class="text-sm">Password changed. Use the new password next time you sign in.</p>
      <form v-else class="flex flex-col gap-4" @submit.prevent="save">
        <div class="flex flex-col gap-1.5">
          <Label for="pw-current">Current password</Label>
          <Input id="pw-current" v-model="form.current" type="password" autocomplete="current-password" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="pw-new">New password</Label>
          <Input id="pw-new" v-model="form.next" type="password" autocomplete="new-password" minlength="8" required />
        </div>
        <div class="flex flex-col gap-1.5">
          <Label for="pw-repeat">Repeat new password</Label>
          <Input id="pw-repeat" v-model="form.repeat" type="password" autocomplete="new-password" minlength="8" required />
        </div>
        <Alert v-if="error" variant="destructive"><AlertDescription>{{ error }}</AlertDescription></Alert>
        <DialogFooter><Button type="submit">Change password</Button></DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
