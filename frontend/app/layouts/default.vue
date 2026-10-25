<script setup lang="ts">
import {
  Bell, BellOff, BellRing, CalendarClock, ChartLine, ChevronDown, Ellipsis, FlaskConical, KeyRound, Languages, LayoutDashboard, LogOut, Moon,
  Building2, Lock, LogOut as Leave, Search, Sprout, Sun, Users, Workflow, X, Cpu, BookOpen, CircuitBoard,
} from "lucide-vue-next";
import type { Component } from "vue";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { roleLabels } from "~/utils/types";

const { user, logout, can, isCompany, activeFarm, setActiveFarm } = useAuth();
// a company account with no customer's farm chosen sees only the company pages
const onFarm = computed(() => !isCompany.value || !!activeFarm.value);
const openAlerts = useState<number>("openAlerts", () => 0);
const passwordOpen = ref(false);
const moreOpen = ref(false);
const route = useRoute();

const critical = useCriticalAlerts();
onMounted(() => { if (onFarm.value) critical.start(); });
onBeforeUnmount(critical.stop);

type NavItem = { to: string; label: string; icon: Component };
const companyGroup = computed(() => isCompany.value ? [{ title: "Smart Farm company", items: [
  { to: "/company", label: "Customer farms", icon: Building2 },
  ...(user.value?.companyRole === "ADMIN" ? [{ to: "/company/staff", label: "Company staff", icon: Users }] : []),
] }] : []);
const groups = computed<{ title: string; items: NavItem[] }[]>(() => !onFarm.value ? companyGroup.value : [
  ...companyGroup.value,
  { title: "Monitor", items: [
    { to: "/", label: "Dashboard", icon: LayoutDashboard },
    { to: "/alerts", label: "Alerts", icon: Bell },
    { to: "/history", label: "History", icon: ChartLine },
  ] },
  { title: "Control", items: [
    { to: "/rules", label: "Automation rules", icon: Workflow },
    { to: "/schedules", label: "Schedules", icon: CalendarClock },
  ] },
  { title: "Equipment", items: [
    { to: "/zones", label: "Zones & devices", icon: Sprout },
    { to: "/devices", label: "Devices", icon: Cpu },
    ...(can("TECHNICIAN") ? [{ to: "/catalog", label: "Device catalog", icon: BookOpen }, { to: "/controllers", label: "Controller boards", icon: CircuitBoard },
      { to: "/farm-lab", label: "Farm Lab", icon: FlaskConical }] : []),
  ] },
  ...(can() ? [{ title: "Admin", items: [{ to: "/users", label: "Users", icon: Users }] }] : []),
]);
const nav = computed(() => groups.value.flatMap((g) => g.items));

// Zones: a farm can have thousands, so the sidebar shows only the ones opened last (plus "Find a zone", Ctrl/⌘+K).
const api = useApi();
type NavZone = { id: number; name: string; type: string; detail?: string | null; cropName?: string | null; lab: boolean;
  sensors: { id: number; name: string; unit: string; status: string; latest: { value: number } | null }[] };
const allZones = ref<NavZone[]>([]);
const zones = computed(() => allZones.value.filter((z) => !z.lab));
const recent = useRecentZones();
const recentZones = computed(() => {
  const byId = new Map(allZones.value.map((z) => [z.id, z]));
  const picked = recent.ids.value.map((id) => byId.get(id)).filter((z): z is NavZone => !!z);
  return (picked.length ? picked : zones.value.slice(0, 3)).slice(0, 5);
});
const pickerOpen = useState("zonePicker.open", () => false);
watch(() => route.params.id, (id) => { if (route.path.startsWith("/zone/") && id) recent.visit(Number(id)); }, { immediate: true });
const zoneNames = computed(() => Object.fromEntries(allZones.value.map((z) => [z.id, z.name])));
async function loadZones() {
  if (!user.value || !onFarm.value) return;
  try {
    allZones.value = (await api<{ zones: typeof allZones.value }>("/api/farm")).zones;
  } catch {
    // the page itself reports connection problems
  }
}
usePolling(loadZones, 30000);
// One black pill behind the current page that glides to the next one (measured from the active link).
const navEl = ref<HTMLElement | null>(null);
const pill = ref<{ top: number; left: number; width: number; height: number } | null>(null);
async function placePill() {
  await nextTick();
  const el = navEl.value?.querySelector<HTMLElement>("a.nav-active");
  pill.value = el ? { top: el.offsetTop, left: el.offsetLeft, width: el.offsetWidth, height: el.offsetHeight } : null;
}
watch(() => [route.path, recentZones.value.length, groups.value.length], placePill);
onMounted(() => {
  placePill();
  window.addEventListener("resize", placePill);
});
onBeforeUnmount(() => window.removeEventListener("resize", placePill));
const zoneDot = (z: { type: string; name: string; detail?: string | null }) =>
  /greenhouse/i.test(`${z.name} ${z.detail ?? ""}`) ? "bg-greenhouse" : z.type === "POULTRY" ? "bg-poultry" : "bg-crop";

// Phones: the four places a worker goes most, the rest under "More"
const tabs = computed(() => !onFarm.value
  ? companyGroup.value[0]!.items.map((i) => ({ to: i.to, label: i.label, icon: i.icon }))
  : [
      { to: "/", label: "Home", icon: LayoutDashboard },
      { to: "/alerts", label: "Alerts", icon: Bell },
      { to: "/history", label: "History", icon: ChartLine },
      { to: "/zones", label: "Zones", icon: Sprout },
    ]);
const moreLinks = computed(() => nav.value.filter((n) => !tabs.value.some((tab) => tab.to === n.to)));
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
// the name under the person's name: their farm role, or their company role
const accountRole = computed(() => !user.value ? "" : user.value.companyRole
  ? (user.value.companyRole === "ADMIN" ? t("Smart Farm admin") : t("Smart Farm support"))
  : t(roleLabels[user.value.role!]));
function leaveFarm() {
  setActiveFarm(null);
  window.location.assign("/company"); // a fresh start, so nothing from the farm stays on screen
}

// a short note after being sent away from a page this role cannot use (see middleware/auth.global.ts)
const pageNotice = useState<string>("pageNotice", () => "");
watch(pageNotice, (v) => { if (v) setTimeout(() => { if (pageNotice.value === v) pageNotice.value = ""; }, 6000); });

// the account menu closes on a click elsewhere or Escape
const userOpen = ref(false);
const userEl = ref<HTMLElement | null>(null);
function closeUser(e: Event) {
  if (userOpen.value && !userEl.value?.contains(e.target as Node)) userOpen.value = false;
}
function escUser(e: KeyboardEvent) {
  if (e.key === "Escape") userOpen.value = false;
}
onMounted(() => { document.addEventListener("mousedown", closeUser); document.addEventListener("keydown", escUser); });
onBeforeUnmount(() => { document.removeEventListener("mousedown", closeUser); document.removeEventListener("keydown", escUser); });
watch(() => route.fullPath, () => (userOpen.value = false));

function toggleLang() {
  setLang(lang.value === "en" ? "km" : "en");
}
</script>

<template>
  <div class="min-h-screen md:grid md:grid-cols-[248px_1fr]">
    <aside class="hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:sticky md:top-0 md:flex md:h-screen md:flex-col">
      <div class="flex items-center gap-2.5 px-6 pt-6 pb-5">
        <div class="flex size-9 items-center justify-center rounded-full bg-linear-to-br from-gold to-crop text-white shadow-soft">
          <Sprout class="size-[18px]" />
        </div>
        <div class="harvest-text font-heading text-xl font-semibold tracking-tight">Smart Farm</div>
      </div>
      <nav ref="navEl" class="relative flex flex-1 flex-col gap-5 overflow-y-auto px-4 pb-4">
        <div v-if="pill" aria-hidden="true" class="nav-pill"
          :style="{ transform: `translate(${pill.left}px, ${pill.top}px)`, width: `${pill.width}px`, height: `${pill.height}px` }" />
        <div v-for="g in groups" :key="g.title">
          <div class="px-3 pb-1.5 text-xs text-sidebar-foreground/60">{{ t(g.title) }}</div>
          <template v-for="item in g.items" :key="item.to">
            <NuxtLink
              :to="item.to"
              class="relative z-10 flex items-center gap-3 rounded-full px-3.5 py-2 text-[15px] transition-colors duration-200 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
              :active-class="item.to === '/' ? '' : 'nav-active'"
              :exact-active-class="item.to === '/' ? 'nav-active' : ''"
            >
              <component :is="item.icon" class="size-[18px]" />
              {{ t(item.label) }}
              <span v-if="item.to === '/alerts' && openAlerts > 0"
                class="num ml-auto min-w-6 rounded-full bg-destructive px-1.5 text-center text-xs leading-5 text-destructive-foreground">{{ openAlerts }}</span>
            </NuxtLink>
            <div v-if="item.to === '/' && allZones.length" class="mt-1 mb-1 ml-6 flex flex-col gap-0.5">
              <button type="button" class="mb-0.5 flex items-center gap-2 rounded-full border border-sidebar-border bg-card/60 px-3 py-1.5 text-left text-sm text-sidebar-foreground/80 transition-colors hover:bg-sidebar-accent"
                @click="pickerOpen = true">
                <Search class="size-3.5 shrink-0" /><span class="flex-1 truncate">{{ t("Find a zone") }}</span>
                <kbd class="rounded border border-sidebar-border px-1 font-sans text-[10px] text-sidebar-foreground/60">⌘K</kbd>
              </button>
              <div class="px-3 pt-1 text-[11px] text-sidebar-foreground/55">{{ recent.ids.value.length ? t("Recent") : t("Zones") }}</div>
              <NuxtLink v-for="z in recentZones" :key="z.id" :to="`/zone/${z.id}`" active-class="!bg-sidebar-accent !text-sidebar-accent-foreground font-medium"
                class="flex items-center gap-2.5 truncate rounded-full px-3 py-1 text-sm text-sidebar-foreground/85 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground">
                <span class="size-2 shrink-0 rounded-full" :class="zoneDot(z)" />
                <span class="truncate">{{ z.name }}</span>
              </NuxtLink>
              <NuxtLink to="/zones" class="px-3 py-1 text-xs text-sidebar-foreground/60 hover:text-sidebar-accent-foreground">{{ t("All zones") }} ({{ allZones.length }}) →</NuxtLink>
            </div>
          </template>
        </div>
      </nav>
    </aside>

    <div class="min-w-0 pb-28 md:pb-0">
      <header class="topbar sticky top-0 z-40 flex items-center justify-end gap-1.5 px-4 py-3 md:px-8">
        <div class="mr-auto flex items-center gap-2 md:hidden">
          <div class="flex size-8 items-center justify-center rounded-full bg-linear-to-br from-gold to-crop text-white">
            <Sprout class="size-4" />
          </div>
          <span class="harvest-text hidden font-heading text-lg font-semibold whitespace-nowrap min-[400px]:inline">Smart Farm</span>
        </div>
        <AlertCenter v-if="onFarm" :zone-names="zoneNames" :zones="allZones" />
        <button type="button" class="round-btn gap-1.5 text-sm sm:w-auto sm:px-3" :aria-label="t('Language')" :title="t('Language')" @click="toggleLang">
          <Languages class="size-4" /> <span class="hidden sm:inline">{{ lang === "en" ? "ខ្មែរ" : "EN" }}</span>
        </button>
        <!-- the person: name and role, with their settings and Log out in one menu -->
        <div ref="userEl" class="relative hidden md:block">
          <button type="button" class="round-btn w-auto gap-2.5 py-0 pr-3 pl-1" aria-haspopup="menu" :aria-expanded="userOpen"
            :aria-label="t('Account menu')" @click="userOpen = !userOpen">
            <span class="flex size-7 items-center justify-center rounded-full bg-ink text-xs font-medium text-ink-foreground">{{ user?.name.slice(0, 1) }}</span>
            <span class="text-left leading-tight"><span class="block text-sm font-medium">{{ user?.name }}</span>
              <span class="block text-[11px] text-muted-foreground">{{ accountRole }}</span></span>
            <ChevronDown class="size-4 text-muted-foreground transition-transform" :class="{ 'rotate-180': userOpen }" />
          </button>
          <Transition name="pop">
            <div v-if="userOpen" role="menu" class="absolute right-0 z-50 mt-2 w-60 origin-top-right rounded-2xl border bg-popover p-1.5 text-sm shadow-soft">
              <button type="button" role="menuitem" class="user-item" :aria-pressed="critical.soundOn.value" @click="userOpen = false; critical.setSound(!critical.soundOn.value)">
                <component :is="critical.soundOn.value ? Bell : BellOff" class="size-4" /> {{ t("Alert sound") }}
                <span class="ml-auto text-xs text-muted-foreground">{{ critical.soundOn.value ? t("On") : t("Off") }}</span>
              </button>
              <button type="button" role="menuitem" class="user-item" @click="userOpen = false; toggleTheme()">
                <component :is="dark ? Sun : Moon" class="size-4" /> {{ t(dark ? "Light mode" : "Dark mode") }}
              </button>
              <button type="button" role="menuitem" class="user-item" @click="userOpen = false; passwordOpen = true">
                <KeyRound class="size-4" /> {{ t("Change password") }}
              </button>
              <div class="my-1 h-px bg-border" />
              <button type="button" role="menuitem" class="user-item text-destructive" @click="userOpen = false; logout()">
                <LogOut class="size-4" /> {{ t("Log out") }}
              </button>
            </div>
          </Transition>
        </div>
        <ChangePasswordDialog v-model:open="passwordOpen" />
      </header>

      <!-- company staff working on a customer's farm: which one, and the way back -->
      <div v-if="isCompany && activeFarm" class="mx-4 mt-1 flex flex-wrap items-center gap-3 rounded-2xl border bg-card px-4 py-2.5 text-sm shadow-soft md:mx-8">
        <span class="badge-disc size-8 shrink-0"><Building2 class="size-4" /></span>
        <span class="min-w-0 flex-1">{{ t("Working on") }} <b class="font-medium">{{ activeFarm.name }}</b>
          <span class="text-muted-foreground"> · {{ user?.companyRole === "ADMIN" ? t("as its owner (company admin)") : t("as a technician (company support)") }}</span></span>
        <button type="button" class="round-btn w-auto gap-1.5 px-3 text-sm" @click="leaveFarm"><Leave class="size-4" /> {{ t("Back to customer farms") }}</button>
      </div>
      <main class="mx-auto max-w-7xl px-4 py-6 md:px-8">
        <Transition name="pop">
          <div v-if="pageNotice" role="status" class="mb-4 flex items-center gap-3 rounded-2xl border bg-card px-4 py-3 text-sm shadow-soft">
            <span class="badge-disc size-8 shrink-0"><Lock class="size-4" /></span>
            <span class="flex-1">{{ pageNotice }}</span>
            <button type="button" class="round-btn size-8" :aria-label="t('Dismiss')" @click="pageNotice = ''"><X class="size-4" /></button>
          </div>
        </Transition>
        <slot />
      </main>
      <ConfirmDialog />
      <Toaster />
      <ZonePicker v-model:open="pickerOpen" :zones="allZones" :current-id="route.path.startsWith('/zone/') ? Number(route.params.id) : null" />
    </div>

    <!-- Phones: a floating pill of round buttons within thumb reach; the current page is the black one -->
    <nav class="fixed inset-x-0 bottom-0 z-40 flex justify-center pb-[calc(env(safe-area-inset-bottom)+0.75rem)] md:hidden" :aria-label="t('Menu')">
      <div class="flex items-center gap-1 rounded-full bg-gold/90 p-1.5 shadow-soft backdrop-blur dark:bg-card">
        <NuxtLink v-for="tab in tabs" :key="tab.to" :to="tab.to" :aria-label="t(tab.label)"
          class="relative flex size-12 items-center justify-center rounded-full bg-accent text-accent-foreground"
          exact-active-class="!bg-ink !text-ink-foreground">
          <component :is="tab.icon" class="size-5" />
          <span class="sr-only">{{ t(tab.label) }}</span>
          <span v-if="tab.to === '/alerts' && openAlerts > 0"
            class="absolute -top-0.5 -right-0.5 min-w-4 rounded-full bg-destructive px-1 text-center text-[10px] leading-4 text-destructive-foreground">{{ openAlerts }}</span>
        </NuxtLink>
        <button type="button" class="flex size-12 items-center justify-center rounded-full bg-accent text-accent-foreground"
          :class="{ '!bg-ink !text-ink-foreground': moreActive }" :aria-label="t('More')" @click="moreOpen = true">
          <Ellipsis class="size-5" />
        </button>
      </div>
    </nav>

    <Dialog v-model:open="moreOpen">
      <DialogContent class="max-w-sm">
        <DialogHeader>
          <DialogTitle>{{ user?.name }}</DialogTitle>
          <DialogDescription v-if="user">{{ accountRole }}</DialogDescription>
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
