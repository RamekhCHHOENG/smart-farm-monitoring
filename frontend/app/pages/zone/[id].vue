<script setup lang="ts">
import { ArrowLeft, ArrowRight, BellRing, CalendarClock, ChevronsUpDown, Power, Search, Timer, Workflow } from "lucide-vue-next";
import { Alert as UiAlert, AlertDescription } from "@/components/ui/alert";
import type { Alert, DeviceLog, Farm, Reading, Rule, Task } from "~/utils/types";
import { alertParts, alertTag } from "~/utils/alertParts";
import { dayStart } from "~/utils/analytics";

// Everything about one zone. The top is a bento that fits one screen (conditions, the zone itself with live values
// pinned on it, its machines and alerts); the rest sits under a sticky section bar: sensors, machines, rules and
// schedules, devices, activity.
const route = useRoute();
const api = useApi();
const zoneId = computed(() => Number(route.params.id));

const farm = ref<Farm | null>(null);
const rules = ref<Rule[]>([]);
const tasks = ref<Task[]>([]);
const alerts = ref<Alert[]>([]);
const readings = ref<Record<number, { t: number; v: number }[]>>({});
const logs = ref<DeviceLog[]>([]);
const error = ref("");

const zone = computed(() => farm.value?.zones.find((z) => z.id === zoneId.value) ?? null);
const zoneRules = computed(() => rules.value.filter((r) => r.zoneId === zoneId.value));
const zoneTasks = computed(() => tasks.value.filter((x) => x.zoneId === zoneId.value).sort((a, b) => a.runTime.localeCompare(b.runTime)));
const zoneAlerts = computed(() => alerts.value.filter((a) => a.zoneId === zoneId.value && a.status === "OPEN"));

async function load() {
  try {
    farm.value = await api<Farm>("/api/farm");
    error.value = "";
  } catch (e) {
    error.value = errorMessage(e);
  }
}
usePolling(load, 4000);
async function loadSlow() {
  try {
    [rules.value, tasks.value, alerts.value] = await Promise.all([
      api<Rule[]>("/api/rules"), api<Task[]>("/api/tasks"), api<Alert[]>("/api/alerts", { query: { status: "OPEN", scope: "ALL" } }),
    ]);
  } catch {
    // the farm load reports connection problems
  }
}
usePolling(loadSlow, 15000);
async function loadAnalytics() {
  const z = zone.value;
  if (!z) return;
  const dayAgo = new Date(Date.now() - 86400000).toISOString();
  const [r, l] = await Promise.all([
    Promise.all(z.sensors.map(async (s) => {
      try {
        const rows = await api<Reading[]>(`/api/sensors/${s.id}/readings`, { query: { from: dayAgo } });
        return [s.id, rows.map((x) => ({ t: Date.parse(x.recordedAt), v: x.value }))] as const;
      } catch {
        return [s.id, readings.value[s.id] ?? []] as const;
      }
    })),
    api<DeviceLog[]>(`/api/zones/${z.id}/logs`, { query: { from: new Date(dayStart(6)).toISOString() } }).catch(() => logs.value),
  ]);
  readings.value = Object.fromEntries(r);
  logs.value = l;
}
usePolling(loadAnalytics, 60000);
watch(() => `${zone.value?.id}:${zone.value?.sensors.length}:${zone.value?.actuators.map((a) => a.status).join()}`, loadAnalytics);
watch(zoneId, () => {
  readings.value = {};
  logs.value = [];
});

const f = useZoneFigures({ zone, rules, readings, logs, criticalOpen: () => zoneAlerts.value.some((a) => a.severity === "CRITICAL") });
const look = computed(() => (zone.value ? zoneLook(zone.value) : null));
const pickerOpen = useState("zonePicker.open", () => false);
// the zones next to this one, for quick stepping (the finder covers the rest, however many there are)
const neighbours = computed(() => {
  const list = (farm.value?.zones ?? []).filter((z) => !z.lab || zone.value?.lab);
  const i = list.findIndex((z) => z.id === zoneId.value);
  return { prev: i > 0 ? list[i - 1] : null, next: i >= 0 && i < list.length - 1 ? list[i + 1] : null, index: i + 1, total: list.length };
});
useHead(() => ({ title: zone.value ? `${zone.value.name} · Smart Farm` : "Zone · Smart Farm" }));

// live values pinned on the zone picture, spread over it
const pins = computed(() => (zone.value?.sensors ?? []).slice(0, 6).map((s, i) => {
  const spots = [[18, 26], [62, 20], [30, 62], [70, 58], [46, 40], [14, 78]];
  return { s, left: spots[i]![0], top: spots[i]![1] };
}));
const actionText = (r: Rule) => !r.actuatorName ? "raise an alert"
  : r.stopAt !== null ? `run ${r.actuatorName} until ${r.stopAt}${r.unit} (max ${r.durationMin} min)` : `run ${r.actuatorName} for ${r.durationMin} min`;
// ----- the activity log: every start, stop and repair, with filters, 25 at a time -----
const activityRange = ref<1 | 7 | 30>(7);
const activityMachine = ref<number | "">("");
const activitySource = ref("");
const activityShown = ref(25);
const activityLogs = ref<DeviceLog[]>([]);
const activityLoading = ref(false);
async function loadActivity() {
  if (!zone.value) return;
  activityLoading.value = true;
  try {
    const from = activityRange.value === 7 ? dayStart(6) : Date.now() - activityRange.value * 86400000;
    activityLogs.value = await api<DeviceLog[]>(`/api/zones/${zone.value.id}/logs`, { query: { from: new Date(from).toISOString() } });
  } catch {
    // keep what is shown
  } finally {
    activityLoading.value = false;
  }
}
watch([activityRange, () => zone.value?.id], () => { activityShown.value = 25; loadActivity(); }, { immediate: true });
usePolling(loadActivity, 30000);
const activityFiltered = computed(() => [...activityLogs.value]
  .filter((l) => (activityMachine.value === "" || l.actuatorId === activityMachine.value) && (!activitySource.value || l.source === activitySource.value))
  .sort((a, b) => b.at.localeCompare(a.at)));
const activitySources = computed(() => [...new Set(activityLogs.value.map((l) => l.source))].sort());
const sourceLabel = (s: string) => ({ RULE: "Rule", MANUAL: "Person", SCHEDULE: "Schedule", SAFETY: "Safety stop", TECHNICIAN: "Technician" } as Record<string, string>)[s] ?? s;
const dayHeading = (iso: string) => {
  const d = new Date(iso);
  const today = new Date();
  const y = new Date(Date.now() - 86400000);
  return d.toDateString() === today.toDateString() ? "Today" : d.toDateString() === y.toDateString() ? "Yesterday"
    : d.toLocaleDateString([], { weekday: "long", day: "numeric", month: "short" });
};
const actionWord = (a: string) => ({ ACTIVATED: "started", DEACTIVATED: "stopped", FAULT: "reported a fault", OFFLINE: "went offline", RESTORE: "back online",
  START_REPAIR: "repair started", COMPLETE_REPAIR: "repair done", DECOMMISSION: "retired" } as Record<string, string>)[a] ?? a.toLowerCase().replace(/_/g, " ");

const sections = [
  { id: "sensors", label: "Sensors" }, { id: "machines", label: "Machines" }, { id: "rules", label: "Rules & schedules" },
  { id: "devices", label: "Devices" }, { id: "activity", label: "Activity" },
];
</script>

<template>
  <div>
    <UiAlert v-if="error" variant="destructive" class="mb-4"><AlertDescription>{{ error }}</AlertDescription></UiAlert>

    <div v-if="!farm" class="flex flex-col gap-5" aria-busy="true">
      <div class="skeleton h-10 w-72" />
      <div class="grid-cols-1 grid gap-5 xl:grid-cols-12"><div class="skeleton h-96 xl:col-span-3" /><div class="skeleton h-96 xl:col-span-6" /><div class="skeleton h-96 xl:col-span-3" /></div>
    </div>

    <div v-else-if="!zone" class="py-16 text-center">
      <p class="text-lg">{{ t("This zone does not exist any more.") }}</p>
      <NuxtLink to="/" class="mt-3 inline-flex items-center gap-1.5 text-sm text-primary"><ArrowLeft class="size-4" /> {{ t("Dashboard") }}</NuxtLink>
    </div>

    <template v-else>
      <!-- header: back, name, zone switcher -->
      <div class="mb-4 flex flex-wrap items-end gap-x-6 gap-y-3">
        <div class="min-w-0">
          <NuxtLink to="/" class="hit inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft class="size-4" /> {{ t("Dashboard") }}</NuxtLink>
          <h1 class="mt-1 flex flex-wrap items-center gap-3 text-[2.1rem] leading-tight font-normal tracking-tight">
            <span class="size-3 rounded-full" :class="look!.dot" /> {{ zone.name }}
            <span class="rounded-full bg-card px-3 py-0.5 text-sm" :class="look!.text">{{ t(zone.type) }}</span>
            <span v-if="zone.lab" class="rounded-full bg-primary/12 px-3 py-0.5 text-sm text-primary">LAB</span>
          </h1>
          <p class="text-sm text-muted-foreground">{{ zone.detail }} · {{ zone.sensors.length }} {{ t("sensors") }} · {{ f.actuators.value.length }} {{ t("machines") }}</p>
        </div>
        <div class="ml-auto flex items-center gap-2" :aria-label="t('Zones')">
          <NuxtLink v-if="neighbours.prev" :to="`/zone/${neighbours.prev.id}`" class="round-btn" :aria-label="`${t('Previous zone')}: ${neighbours.prev.name}`" :title="neighbours.prev.name">
            <ArrowLeft class="size-4" /></NuxtLink>
          <button type="button" class="round-btn w-auto gap-2 px-3.5 text-sm" @click="pickerOpen = true">
            <Search class="size-4 text-muted-foreground" /> {{ t("Switch zone") }}
            <span class="num text-xs text-muted-foreground">{{ neighbours.index }}/{{ neighbours.total }}</span>
            <ChevronsUpDown class="size-4 text-muted-foreground" />
          </button>
          <NuxtLink v-if="neighbours.next" :to="`/zone/${neighbours.next.id}`" class="round-btn" :aria-label="`${t('Next zone')}: ${neighbours.next.name}`" :title="neighbours.next.name">
            <ArrowRight class="size-4" /></NuxtLink>
        </div>
      </div>

      <!-- the bento: one screen -->
      <div class="grid-cols-1 stagger grid gap-5 xl:h-[max(30rem,calc(100vh-13rem))] xl:grid-cols-12">
        <!-- current conditions -->
        <section class="flex min-h-0 flex-col gap-4 overflow-y-auto rounded-[1.75rem] border bg-card p-5 shadow-soft xl:col-span-3">
          <h2 class="text-lg">{{ t("Current conditions") }}</h2>
          <div v-if="!zone.sensors.length" class="flex flex-1 flex-col items-center justify-center gap-3 rounded-2xl border border-dashed p-6 text-center">
            <p class="text-sm text-muted-foreground">{{ t("No sensors in this zone yet, so there is nothing to measure.") }}</p>
            <NuxtLink :to="{ path: '/devices/new', query: { zone: zone.id } }" class="inline-flex h-9 items-center rounded-full bg-ink px-4 text-sm text-ink-foreground hover:bg-ink/85">+ {{ t("Add a sensor") }}</NuxtLink>
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <NuxtLink v-for="s in zone.sensors.slice(0, 4)" :key="s.id" :to="{ path: '/history', query: { zone: zone.id, sensor: s.id } }"
              class="rounded-2xl bg-muted/70 p-3 transition-colors hover:bg-accent">
              <div class="truncate text-[11px] text-muted-foreground">{{ s.measurementName ?? s.name }}</div>
              <div class="num mt-0.5 text-2xl leading-none" :class="{ 'text-destructive': f.alarmOf(s.id) }">
                <AnimatedNumber :value="s.latest?.value ?? null" :decimals="1" /><span class="ml-0.5 font-sans text-[11px] text-muted-foreground">{{ s.unit }}</span>
              </div>
              <Sparkline class="mt-2 w-full" :values="(readings[s.id] ?? []).map((p) => p.v)" :min="s.minValid" :max="s.maxValid" :alarm="f.alarmOf(s.id)" />
            </NuxtLink>
          </div>
          <div v-if="zone.sensors.length">
            <h3 class="mb-2 text-xs text-muted-foreground">{{ t("Time in range, last 24 hours") }}</h3>
            <ul class="flex flex-col gap-2">
              <li v-for="s in zone.sensors" :key="s.id" class="text-sm">
                <div class="flex justify-between"><span class="truncate">{{ s.name }}</span>
                  <span class="num" :class="(f.statsOf(s.id)?.inRange ?? 100) >= 90 ? 'text-primary' : (f.statsOf(s.id)?.inRange ?? 100) >= 70 ? 'text-warning' : 'text-destructive'">
                    {{ f.statsOf(s.id)?.inRange == null ? "–" : `${f.statsOf(s.id)!.inRange}%` }}</span></div>
                <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-muted"><div class="h-full rounded-full bg-primary transition-[width] duration-700" :style="{ width: `${f.statsOf(s.id)?.inRange ?? 0}%` }" /></div>
              </li>
            </ul>
          </div>
          <div v-if="f.actuators.value.length">
            <h3 class="mb-2 text-xs text-muted-foreground">{{ t("Machine run time, last 7 days") }}</h3>
            <BarChart :labels="f.week.value.labels.map((l) => l.slice(0, 2))" :series="f.week.value.series" unit=" min" :height="64" class="mini-bars" />
          </div>
          <dl class="mt-auto flex flex-col gap-2 border-t pt-3 text-sm">
            <div class="flex items-center gap-2"><Timer class="size-4 text-muted-foreground" /><dt class="flex-1 text-muted-foreground">{{ t("Run time today") }}</dt>
              <dd class="num">{{ Math.round(f.kpis.value.runMin) }} min <span class="text-xs text-muted-foreground">/ {{ Math.round(f.kpis.value.runYesterday) }} yesterday</span></dd></div>
            <div class="flex items-center gap-2"><Power class="size-4 text-muted-foreground" /><dt class="flex-1 text-muted-foreground">{{ t("Starts today") }}</dt>
              <dd class="num">{{ f.kpis.value.starts }} <span class="text-xs text-muted-foreground">/ {{ f.kpis.value.startsYesterday }} yesterday</span></dd></div>
            <div class="flex items-center gap-2"><BellRing class="size-4 text-muted-foreground" /><dt class="flex-1 text-muted-foreground">{{ t("Open alerts") }}</dt>
              <dd class="num" :class="{ 'text-destructive': zoneAlerts.length }">{{ zoneAlerts.length }}</dd></div>
          </dl>
        </section>

        <!-- the zone itself, with live values pinned on it -->
        <section class="field relative min-h-[24rem] overflow-hidden rounded-[1.75rem] text-white xl:col-span-6 xl:min-h-0" :class="look!.field">
          <div v-if="f.health.value && f.health.value.stress >= 30" class="field-heat" :style="{ opacity: Math.min(1, f.health.value.stress / 100) }" />
          <div class="absolute top-5 left-5 drop-shadow-[0_1px_2px_rgb(0_0_0/45%)]">
            <div class="text-2xl font-medium">{{ zone.cropName ?? (zone.birdCount ? `${zone.birdCount} birds` : look!.kind) }}</div>
            <div class="text-sm text-white/85">{{ t(look!.kind) }} · {{ zone.name }}</div>
          </div>
          <NuxtLink v-for="p in pins" :key="p.s.id" :to="{ path: '/history', query: { zone: zone.id, sensor: p.s.id } }"
            class="pin absolute flex items-center gap-2 rounded-full bg-black/40 py-1 pr-3 pl-1 text-sm backdrop-blur-md transition-transform hover:scale-105"
            :style="{ left: `${p.left}%`, top: `${p.top}%` }">
            <span class="flex size-6 items-center justify-center rounded-full" :class="f.alarmOf(p.s.id) ? 'bg-destructive' : 'bg-white/25'">
              <span class="size-2 rounded-full bg-white" :class="{ 'animate-ping': f.alarmOf(p.s.id) }" />
            </span>
            <span class="num">{{ p.s.latest ? p.s.latest.value.toFixed(1) : "–" }}{{ p.s.unit }}</span>
            <span class="hidden text-white/80 sm:inline">{{ p.s.measurementName ?? p.s.name }}</span>
          </NuxtLink>
          <div v-if="f.health.value" class="absolute right-4 bottom-4 rounded-2xl bg-card/92 p-3 text-foreground shadow-soft backdrop-blur">
            <SemiGauge :value="f.health.value.stress" :size="150" />
            <div class="text-center text-xs text-muted-foreground">{{ t("Stress index") }} · {{ f.health.value.level }}</div>
          </div>
        </section>

        <!-- machines and alerts -->
        <section class="flex min-h-0 flex-col gap-3 overflow-y-auto rounded-[1.75rem] border bg-card py-5 shadow-soft xl:col-span-3">
          <div class="flex items-center justify-between px-5">
            <h2 class="text-lg">{{ t("Machines") }}</h2>
            <span class="text-xs text-muted-foreground">{{ f.actuators.value.filter((a) => a.status === "RUNNING").length }} {{ t("running") }}</span>
          </div>
          <div class="divide-y">
            <ActuatorControl v-for="a in f.actuators.value" :key="a.id" :actuator="a" @changed="load" />
            <p v-if="!f.actuators.value.length" class="px-5 text-sm text-muted-foreground">{{ t("No machines in this zone.") }}</p>
          </div>
          <div class="mt-auto px-5">
            <h3 class="mb-2 text-sm font-medium">{{ t("Open alerts") }}</h3>
            <ul class="flex flex-col gap-1.5">
              <li v-for="a in zoneAlerts.slice(0, 3)" :key="a.id" class="flex items-start gap-2 rounded-xl bg-muted/60 px-3 py-2">
                <div class="min-w-0 flex-1 text-xs"><div class="truncate font-medium">{{ alertParts(a).subject ?? alertParts(a).problem }}</div>
                  <div class="text-muted-foreground">{{ timeAgo(a.lastRaisedAt) }}</div></div>
                <span class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-medium" :class="alertTag(a).tone">{{ alertTag(a).label }}</span>
              </li>
              <li v-if="!zoneAlerts.length" class="rounded-xl bg-primary/8 px-3 py-2 text-xs text-primary">{{ t("No open alerts. All quiet.") }}</li>
            </ul>
          </div>
        </section>
      </div>

      <!-- sticky section bar -->
      <nav class="topbar sticky top-[60px] z-30 -mx-4 my-6 flex gap-1 overflow-x-auto px-4 py-2 md:-mx-8 md:px-8" :aria-label="t('Sections')">
        <a v-for="s in sections" :key="s.id" :href="`#${s.id}`"
          class="shrink-0 rounded-full border bg-card px-3.5 py-1.5 text-sm text-muted-foreground shadow-soft transition-colors hover:bg-accent hover:text-foreground">{{ t(s.label) }}</a>
      </nav>

      <section id="sensors" class="scroll-mt-32">
        <div class="mb-3 flex items-baseline justify-between"><h2 class="text-2xl">{{ t("Sensors") }}</h2><span class="text-sm text-muted-foreground">{{ t("last 24 hours") }}</span></div>
        <div class="grid-cols-1 stagger grid gap-4 md:grid-cols-2 2xl:grid-cols-3">
          <SensorCard v-for="s in zone.sensors" :key="s.id" :sensor="s" :points="readings[s.id] ?? []" :limits="f.limitsOf(s.id)"
            :stats="f.statsOf(s.id)" :alarm="f.alarmOf(s.id)" :chart-height="100" />
        </div>
        <p v-if="!zone.sensors.length" class="text-sm text-muted-foreground">{{ t("No sensors yet.") }}</p>
      </section>

      <section id="machines" class="mt-8 scroll-mt-32">
        <h2 class="mb-3 text-2xl">{{ t("Machines") }}</h2>
        <div class="grid-cols-1 grid gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
          <div class="overflow-hidden rounded-[1.75rem] border bg-card py-2 shadow-soft">
            <div class="flex justify-between px-5 pt-3 pb-1 text-xs text-muted-foreground"><span>{{ t("Controls") }}</span><span>{{ t("runs in the last 24 hours") }}</span></div>
            <div class="divide-y">
              <MachineRow v-for="a in f.actuators.value" :key="a.id" :actuator="a" :runs="f.runsOf(a.id)" :from="f.dayAgo.value" :to="f.now.value"
                :color="f.colorOf(a.id)" @changed="load" />
            </div>
          </div>
          <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
            <div class="mb-3 flex items-baseline justify-between">
              <h3 class="font-medium">{{ t("Run time, last 7 days") }}</h3>
              <span class="text-xs text-muted-foreground">{{ t("total") }} <span class="num text-sm text-foreground">{{ fmtMinutes(f.weekTotal.value) }}</span></span>
            </div>
            <BarChart :labels="f.week.value.labels" :series="f.week.value.series" unit=" min" :height="150" />
          </div>
        </div>
      </section>

      <section id="rules" class="mt-8 scroll-mt-32">
        <h2 class="mb-3 text-2xl">{{ t("Rules & schedules") }}</h2>
        <div class="grid-cols-1 grid gap-5 lg:grid-cols-2">
          <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
            <div class="mb-3 flex items-center gap-2"><Workflow class="size-4 text-muted-foreground" /><h3 class="font-medium">{{ t("Automation rules") }}</h3>
              <NuxtLink :to="{ path: '/rules', query: { zone: zone.id } }" class="hit ml-auto inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">{{ t("Manage") }} <ArrowRight class="size-3.5" /></NuxtLink></div>
            <ul class="flex flex-col gap-2">
              <li v-for="r in zoneRules" :key="r.id" class="rounded-2xl bg-muted/60 px-3.5 py-2.5" :class="{ 'opacity-55': !r.active }">
                <div class="flex items-center gap-2"><span class="truncate text-sm font-medium">{{ r.name }}</span>
                  <StatusBadge :status="r.active ? 'ACTIVE' : 'PAUSED'" class="ml-auto" /></div>
                <p class="mt-0.5 text-xs text-muted-foreground">
                  When <b class="font-medium text-foreground">{{ r.sensorName }}</b> is {{ r.condition === "BELOW" ? "below" : "above" }}
                  <b class="num font-medium text-foreground">{{ r.threshold }}{{ r.unit }}</b>, {{ actionText(r) }}.
                  <template v-if="r.lastTriggeredAt"> Last {{ timeAgo(r.lastTriggeredAt) }}.</template>
                </p>
              </li>
              <li v-if="!zoneRules.length" class="text-sm text-muted-foreground">{{ t("No rules for this zone.") }}</li>
            </ul>
          </div>
          <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
            <div class="mb-3 flex items-center gap-2"><CalendarClock class="size-4 text-muted-foreground" /><h3 class="font-medium">{{ t("Schedules") }}</h3>
              <NuxtLink to="/schedules" class="hit ml-auto inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground">{{ t("Manage") }} <ArrowRight class="size-3.5" /></NuxtLink></div>
            <ul class="flex flex-col gap-2">
              <li v-for="task in zoneTasks" :key="task.id" class="flex items-center gap-3 rounded-2xl bg-muted/60 px-3.5 py-2.5" :class="{ 'opacity-55': !task.active }">
                <span class="num w-12 text-lg">{{ task.runTime.slice(0, 5) }}</span>
                <div class="min-w-0 flex-1"><div class="truncate text-sm font-medium">{{ task.name }}</div>
                  <div class="text-xs text-muted-foreground">{{ task.actuatorName }} · {{ task.durationMin }} min</div></div>
                <div class="flex gap-0.5">
                  <span v-for="d in ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']" :key="d"
                    class="flex size-5 items-center justify-center rounded-full text-[10px]"
                    :class="task.repeatDays.includes(d) ? 'bg-primary text-primary-foreground' : 'bg-card text-muted-foreground'">{{ d.charAt(0) }}</span>
                </div>
              </li>
              <li v-if="!zoneTasks.length" class="text-sm text-muted-foreground">{{ t("No schedules for this zone.") }}</li>
            </ul>
          </div>
        </div>
      </section>

      <section id="devices" class="mt-8 scroll-mt-32">
        <div class="mb-3 flex items-baseline justify-between"><h2 class="text-2xl">{{ t("Devices") }}</h2>
          <NuxtLink :to="{ path: '/devices/new', query: { zone: zone.id } }" class="hit text-sm text-muted-foreground hover:text-foreground">+ {{ t("Add device") }}</NuxtLink></div>
        <div class="stagger grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
          <div v-for="d in zone.devices" :key="d.id" class="lift flex flex-col gap-2 rounded-[1.4rem] border bg-card p-3 shadow-soft">
            <div class="stage rounded-2xl p-1.5">
              <DevicePicture :model-id="d.modelId" :has-picture="d.hasPicture" :wokwi-part="d.wokwiPart" :art-type="d.artType"
                :kind="d.category === 'ACTUATOR' ? 'actuator' : 'sensor'" :height="96" />
            </div>
            <div class="min-w-0 leading-tight">
              <div class="truncate text-sm font-medium">{{ d.name }}</div>
              <div class="truncate text-xs text-muted-foreground">{{ d.modelName }}</div>
              <div class="truncate text-[11px] text-muted-foreground">{{ d.controllerName ? `board ${d.controllerName}` : "no board" }}<template v-if="d.serialNumber"> · {{ d.serialNumber }}</template></div>
            </div>
          </div>
        </div>
        <p v-if="!zone.devices.length" class="text-sm text-muted-foreground">{{ t("No devices yet.") }}</p>
      </section>

      <section id="activity" class="mt-8 scroll-mt-32">
        <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
          <h2 class="text-2xl">{{ t("Activity") }} <span class="text-base text-muted-foreground">· {{ activityFiltered.length }}</span></h2>
          <NuxtLink :to="{ path: '/history', query: { zone: zone.id } }" class="hit text-sm text-muted-foreground hover:text-foreground">{{ t("Readings history") }} →</NuxtLink>
        </div>
        <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <div class="mb-4 flex flex-wrap items-center gap-2">
            <div class="flex rounded-full border bg-muted/50 p-0.5" role="group" :aria-label="t('Time range')">
              <button v-for="r in [1, 7, 30] as const" :key="r" type="button" class="rounded-full px-3.5 py-2 text-sm transition-colors sm:py-1"
                :class="activityRange === r ? 'bg-ink text-ink-foreground' : 'text-muted-foreground hover:text-foreground'" @click="activityRange = r">
                {{ r === 1 ? t("24 hours") : t("{n} days", { n: r }) }}</button>
            </div>
            <select v-model="activityMachine" class="h-8 rounded-full border bg-card px-3 text-sm" :aria-label="t('Machine')">
              <option value="">{{ t("All machines") }}</option>
              <option v-for="a in zone.actuators" :key="a.id" :value="a.id">{{ a.name }}</option>
            </select>
            <select v-model="activitySource" class="h-8 rounded-full border bg-card px-3 text-sm" :aria-label="t('Done by')">
              <option value="">{{ t("Done by anyone") }}</option>
              <option v-for="src in activitySources" :key="src" :value="src">{{ t(sourceLabel(src)) }}</option>
            </select>
            <span v-if="activityLoading" class="text-xs text-muted-foreground">{{ t("Loading…") }}</span>
          </div>
          <ol>
            <template v-for="(l, i) in activityFiltered.slice(0, activityShown)" :key="l.id">
              <li v-if="i === 0 || dayHeading(l.at) !== dayHeading(activityFiltered[i - 1]!.at)" class="mt-2 mb-2 text-xs font-medium text-muted-foreground first:mt-0">{{ t(dayHeading(l.at)) }}</li>
              <li class="relative flex gap-3 pb-3.5 pl-6">
                <span class="absolute top-1 left-0 size-3 rounded-full border-2 border-card" :style="{ background: f.colorOf(l.actuatorId) }" />
                <span class="absolute top-4 bottom-0 left-[5px] w-px bg-border" />
                <div class="min-w-0 flex-1 text-sm"><b class="font-medium">{{ l.actuatorName }}</b> {{ actionWord(l.action) }}
                  <span class="ml-1 rounded-full bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">{{ t(sourceLabel(l.source)) }}<template v-if="l.userName"> · {{ l.userName }}</template></span>
                  <div v-if="l.detail" class="mt-0.5 text-xs text-muted-foreground">{{ l.detail }}</div></div>
                <time class="num shrink-0 text-xs text-muted-foreground">{{ new Date(l.at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) }}</time>
              </li>
            </template>
            <li v-if="!activityFiltered.length && !activityLoading" class="text-sm text-muted-foreground">{{ t("Nothing matches in this time range.") }}</li>
          </ol>
          <div v-if="activityFiltered.length > activityShown" class="mt-2 flex justify-center">
            <button type="button" class="round-btn w-auto px-5 text-sm" @click="activityShown += 25">
              {{ t("Load more") }} <span class="ml-1.5 text-muted-foreground">({{ activityFiltered.length - activityShown }} {{ t("left") }})</span>
            </button>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.pin { transform: translate(-50%, -50%); }
/* the small run-time chart in the conditions card: no legend, it is the same as the full one below */
.mini-bars :deep(> div:last-child) { display: none; }
.pin:hover { transform: translate(-50%, -50%) scale(1.05); }
</style>
