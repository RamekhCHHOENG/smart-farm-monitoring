<script setup lang="ts">
import { KeyRound, Languages, Sprout } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { User } from "~/utils/types";

// Opened from the link Smart Farm sent: the person sets their own password and is signed in.
definePageMeta({ layout: false });

const route = useRoute();
const token = computed(() => String(route.params.token));
const info = ref<{ name: string; username: string; farmName: string | null; companyRole: string | null } | null>(null);
const problem = ref("");
const password = ref("");
const repeat = ref("");
const error = ref("");
const busy = ref(false);

onMounted(async () => {
  try {
    const url: string = `/api/auth/invites/${token.value}`;
    info.value = await $fetch<{ name: string; username: string; farmName: string | null; companyRole: string | null }>(url);
  } catch (e) {
    problem.value = errorMessage(e);
  }
});

async function submit() {
  error.value = "";
  if (password.value.length < 8) { error.value = t("Use at least 8 characters."); return; }
  if (password.value !== repeat.value) { error.value = t("The two passwords are not the same."); return; }
  busy.value = true;
  try {
    const url: string = `/api/auth/invites/${token.value}`;
    const res = await $fetch<{ token: string; user: User }>(url, { method: "POST", body: { password: password.value } });
    const auth = useAuth();
    auth.signedIn(res);
    await navigateTo(auth.isCompany.value ? "/company" : "/");
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center px-4">
    <Button variant="ghost" size="sm" class="absolute top-3 right-3" :aria-label="t('Language')" @click="setLang(lang === 'en' ? 'km' : 'en')">
      <Languages /> {{ lang === "en" ? "ខ្មែរ" : "EN" }}
    </Button>
    <div class="w-full max-w-sm rounded-[1.75rem] border bg-card p-7 shadow-soft">
      <div class="mb-5 flex items-center gap-2.5">
        <div class="flex size-10 items-center justify-center rounded-full bg-linear-to-br from-gold to-crop text-white"><Sprout class="size-5" /></div>
        <span class="harvest-text font-heading text-xl font-semibold">Smart Farm</span>
      </div>
      <div v-if="!info && !problem" class="flex flex-col gap-3" aria-busy="true"><div class="skeleton h-8 w-3/4" /><div class="skeleton h-24" /></div>
      <div v-else-if="problem" class="flex flex-col gap-3">
        <h1 class="text-2xl">{{ t("This link no longer works") }}</h1>
        <p class="text-sm text-muted-foreground">{{ problem }}</p>
        <NuxtLink to="/login" class="text-sm text-primary">{{ t("Go to sign in") }} →</NuxtLink>
      </div>
      <form v-else class="flex flex-col gap-4" @submit.prevent="submit">
        <div>
          <h1 class="text-2xl leading-tight">{{ t("Welcome, {name}", { name: info!.name }) }}</h1>
          <p class="mt-1.5 text-sm text-muted-foreground">
            <template v-if="info!.farmName">{{ t("Set your password for {farm}.", { farm: info!.farmName }) }}</template>
            <template v-else>{{ t("Set your password for your Smart Farm staff account.") }}</template>
            {{ t("Your username is") }} <b class="font-mono text-foreground">{{ info!.username }}</b>.
          </p>
        </div>
        <div class="flex flex-col gap-1.5"><Label for="iv-pass">{{ t("New password") }}</Label>
          <Input id="iv-pass" v-model="password" type="password" autocomplete="new-password" required minlength="8" /></div>
        <div class="flex flex-col gap-1.5"><Label for="iv-repeat">{{ t("Repeat the password") }}</Label>
          <Input id="iv-repeat" v-model="repeat" type="password" autocomplete="new-password" required minlength="8" /></div>
        <FormError :message="error" />
        <Button type="submit" :disabled="busy"><KeyRound /> {{ t("Set password and sign in") }}</Button>
      </form>
    </div>
  </div>
</template>
