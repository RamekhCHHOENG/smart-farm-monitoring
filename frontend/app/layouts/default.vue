<script setup lang="ts">
import {
  Bell, BellOff, BellRing, CalendarClock, ChartLine, Ellipsis, FlaskConical, KeyRound, Languages, LayoutDashboard, LogOut, Moon,
  Sprout, Sun, Users, Workflow, X,
} from "lucide-vue-next";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { roleLabels } from "~/utils/types";

const { user, logout, can } = useAuth();
const openAlerts = useState<number>("openAlerts", () => 0);
const passwordOpen = ref(false);
const moreOpen = ref(false);
const route = useRoute();

const critical = useCriticalAlerts();
onMounted(critical.start);
onBeforeUnmount(critical.stop);

const nav = computed(() => [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/zones", label: "Zones & devices", icon: Sprout },
  { to: "/rules", label: "Automation rules", icon: Workflow },
  { to: "/schedules", label: "Schedules", icon: CalendarClock },
  { to: "/alerts", label: "Alerts", icon: Bell },
  { to: "/history", label: "History", icon: ChartLine },
  ...(can("TECHNICIAN") ? [{ to: "/farm-lab", label: "Farm Lab", icon: FlaskConical }] : []),
  ...(can() ? [{ to: "/users", label: "Users", icon: Users }] : []),
]);

// Phones: the four places a worker goes most, the rest under "More"
const tabs = [
  { to: "/", label: "Home", icon: LayoutDashboard },
  { to: "/alerts", label: "Alerts", icon: Bell },
  { to: "/history", label: "History", icon: ChartLine },
  { to: "/zones", label: "Zones", icon: Sprout },
];
const moreLinks = computed(() => nav.value.filter((n) => !tabs.some((tab) => tab.to === n.to)));
const moreActive = computed(() => moreLinks.value.some((n) => route.path.startsWith(n.to)));
watch(() => route.path, () => (moreOpen.value = false));

const dark = ref(false);
onMounted(() => {
  try {
    dark.value = localStorage.getItem("smartfarm.theme") === "dark";
  } catch {
    dark.value = false;
  }
  document.documentElement.classList.toggle("dark", dark.value);
  document.documentElement.lang = lang.value;
});
function toggleTheme() {
  dark.value = !dark.value;
  document.documentElement.classList.toggle("dark", dark.value);
  try {
    localStorage.setItem("smartfarm.theme", dark.value ? "dark" : "light");
  } catch {
    // ignore blocked storage
  }
}
function toggleLang() {
  setLang(lang.value === "en" ? "km" : "en");
}
</script>

<template>
  <div class="min-h-screen md:grid md:grid-cols-[240px_1fr]">
    <aside class="hidden border-r bg-card md:sticky md:top-0 md:block md:h-screen">
      <div class="flex items-center gap-2 px-5 py-4">
        <div class="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Sprout class="size-4" />
        </div>
        <div class="leading-tight">
          <div class="text-sm font-semibold">Smart Farm</div>
          <div class="text-xs text-muted-foreground">Monitoring System</div>
        </div>
      </div>
      <nav class="flex flex-col gap-1 px-3 pb-3">
        <NuxtLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          active-class="!bg-accent !text-accent-foreground font-medium"
        >
          <component :is="item.icon" class="size-4" />
          {{ t(item.label) }}
          <Badge v-if="item.to === '/alerts' && openAlerts > 0" variant="destructive" class="ml-auto">{{ openAlerts }}</Badge>
        </NuxtLink>
      </nav>
    </aside>

    <div class="min-w-0 pb-20 md:pb-0">
      <header class="flex items-center justify-end gap-1 border-b bg-card px-4 py-2.5 md:gap-2 md:bg-transparent md:px-8 md:py-3">
        <div class="mr-auto flex items-center gap-2 md:hidden">
          <div class="flex size-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Sprout class="size-3.5" />
          </div>
          <span class="text-sm font-semibold">Smart Farm</span>
        </div>
        <div v-if="user" class="hidden text-sm md:block">
          <span class="font-medium">{{ user.name }}</span>
          <span class="text-muted-foreground"> · {{ t(roleLabels[user.role]) }}</span>
        </div>
        <Button variant="ghost" size="sm" :aria-label="t('Language')" :title="t('Language')" @click="toggleLang">
          <Languages /> {{ lang === "en" ? "ខ្មែរ" : "EN" }}
        </Button>
        <Button variant="ghost" size="icon-sm" class="hidden md:inline-flex"
          :aria-label="t('Alert sound')" :aria-pressed="critical.soundOn.value"
          :title="`${t('Sound and pop-up for CRITICAL alerts')}: ${critical.soundOn.value ? t('On') : t('Off')}`"
          @click="critical.setSound(!critical.soundOn.value)">
          <BellRing v-if="critical.soundOn.value" class="text-primary" />
          <BellOff v-else />
        </Button>
        <Button variant="ghost" size="icon-sm" class="hidden md:inline-flex" :aria-label="t(dark ? 'Light mode' : 'Dark mode')" @click="toggleTheme">
          <Sun v-if="dark" />
          <Moon v-else />
        </Button>
        <Button variant="ghost" size="icon-sm" class="hidden md:inline-flex" :aria-label="t('Change password')" :title="t('Change password')" @click="passwordOpen = true">
          <KeyRound />
        </Button>
        <Button variant="outline" size="sm" class="hidden md:inline-flex" @click="logout">
          <LogOut /> {{ t("Log out") }}
        </Button>
        <ChangePasswordDialog v-model:open="passwordOpen" />
      </header>

      <div v-if="critical.latest.value" role="alert"
        class="z-30 flex items-start gap-3 border-b md:sticky md:top-0 border-destructive/30 bg-destructive/10 px-4 py-3 text-sm md:px-8">
        <BellRing class="mt-0.5 size-4 shrink-0 text-destructive" />
        <div class="min-w-0 flex-1">
          <div class="font-semibold text-destructive">{{ t("New critical alert") }}</div>
          <div>{{ critical.latest.value.message }}</div>
        </div>
        <Button size="sm" variant="outline" as-child><NuxtLink to="/alerts" @click="critical.dismiss()">{{ t("Alerts") }}</NuxtLink></Button>
        <Button size="icon-sm" variant="ghost" :aria-label="t('Dismiss')" @click="critical.dismiss()"><X /></Button>
      </div>

      <main class="mx-auto max-w-7xl px-4 py-6 md:px-8">
        <slot />
      </main>
    </div>

    <!-- Phones: bottom tab bar within thumb reach -->
    <nav class="fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t bg-card pb-[env(safe-area-inset-bottom)] md:hidden" :aria-label="t('Menu')">
      <NuxtLink v-for="tab in tabs" :key="tab.to" :to="tab.to"
        class="relative flex flex-col items-center gap-0.5 py-2 text-[11px] text-muted-foreground"
        exact-active-class="!text-primary font-medium">
        <component :is="tab.icon" class="size-5" />
        <span class="max-w-full truncate px-1">{{ t(tab.label) }}</span>
        <Badge v-if="tab.to === '/alerts' && openAlerts > 0" variant="destructive" class="absolute top-1 left-1/2 ml-1.5 h-4 min-w-4 px-1 text-[10px]">{{ openAlerts }}</Badge>
      </NuxtLink>
      <button type="button" class="flex flex-col items-center gap-0.5 py-2 text-[11px] text-muted-foreground"
        :class="{ '!text-primary font-medium': moreActive }" @click="moreOpen = true">
        <Ellipsis class="size-5" />
        <span>{{ t("More") }}</span>
      </button>
    </nav>

    <Dialog v-model:open="moreOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ user?.name }}</DialogTitle>
          <DialogDescription v-if="user">{{ t(roleLabels[user.role]) }}</DialogDescription>
        </DialogHeader>
        <div class="flex flex-col gap-1">
          <NuxtLink v-for="item in moreLinks" :key="item.to" :to="item.to"
            class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm hover:bg-accent" active-class="bg-accent font-medium">
            <component :is="item.icon" class="size-4" /> {{ t(item.label) }}
          </NuxtLink>
          <div class="my-1 border-t" />
          <button type="button" class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm hover:bg-accent" @click="toggleLang">
            <Languages class="size-4" /> {{ t("Language") }}: <span class="ml-auto font-medium">{{ lang === "en" ? "English" : "ខ្មែរ" }}</span>
          </button>
          <button type="button" class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm hover:bg-accent" @click="critical.setSound(!critical.soundOn.value)">
            <BellRing class="size-4" /> {{ t("Alert sound") }}: <span class="ml-auto font-medium">{{ critical.soundOn.value ? t("On") : t("Off") }}</span>
          </button>
          <button type="button" class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm hover:bg-accent" @click="toggleTheme">
            <component :is="dark ? Sun : Moon" class="size-4" /> {{ t(dark ? "Light mode" : "Dark mode") }}
          </button>
          <button type="button" class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm hover:bg-accent" @click="moreOpen = false; passwordOpen = true">
            <KeyRound class="size-4" /> {{ t("Change password") }}
          </button>
          <button type="button" class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm text-destructive hover:bg-accent" @click="logout">
            <LogOut class="size-4" /> {{ t("Log out") }}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
