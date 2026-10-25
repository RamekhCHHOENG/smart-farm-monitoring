<script setup lang="ts">
import { BellRing, CalendarClock, Check, Droplets, Gauge, MapPin, Power, Sun, Timer, Waves, Wheat } from "lucide-vue-next";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { Alert as FarmAlert, DeviceLog, Farm, Reading, Rule, Task } from "~/utils/types";
import { averageOf, farmSummary, zoneHealth } from "~/utils/farmHealth";
import { dayStart, runsFromLogs, sensorStats } from "~/utils/analytics";

const api = useApi();
const { user } = useAuth();
const farm = ref<Farm | null>(null);
const rules = ref<Rule[]>([]);
const tasks = ref<Task[]>([]);
// Connection problems clear themselves when the server answers again;
// errors from a button show next to that button (ActuatorControl).
const connectionError = ref("");
const openAlerts = useState<number>("openAlerts", () => 0);
const critical = useCriticalAlerts().critical;

async function load() {
  try {
    const [f, r] = await Promise.all([api<Farm>("/api/farm"), api<Rule[]>("/api/rules")]);
    farm.value = f;
    rules.value = r;
    openAlerts.value = f.openAlerts;
    connectionError.value = "";
  } catch (e) {
    connectionError.value = `${errorMessage(e)} ${t("Retrying every 4 seconds...")}`;
  }
}
usePolling(load, 4000);
async function loadTasks() {
  try {
    tasks.value = await api<Task[]>("/api/tasks");
  } catch {
    // the farm load reports connection problems
  }
}
usePolling(loadTasks, 30000);

// Farm Lab test zones stay out of the farm's dashboard unless asked for (remembered in this browser).
const showLab = ref(false);
onMounted(() => {
  try {
    showLab.value = localStorage.getItem("smartfarm.showLabZones") === "1";
  } catch {
    // ignore blocked storage
  }
});
watch(showLab, (v) => {
  try {
    localStorage.setItem("smartfarm.showLabZones", v ? "1" : "0");
  } catch {
    // ignore blocked storage
  }
});
const labZoneCount = computed(() => (farm.value?.zones ?? []).filter((z) => z.lab).length);
const shownZones = computed(() => (farm.value?.zones ?? []).filter((z) => showLab.value || !z.lab));

// ----- the greeting card: the farm's temperature and the readings a farmer checks first -----
const hour = new Date().getHours();
const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
const firstName = computed(() => user.value?.name.split(" ")[0] ?? "");
const temperature = computed(() => averageOf(shownZones.value, "TEMPERATURE"));
const pills = computed(() => [
  { code: "SOIL_MOISTURE", label: "Soil moisture", unit: "%", icon: Droplets },
  { code: "HUMIDITY", label: "Humidity", unit: "%", icon: Waves },
  { code: "LIGHT_LEVEL", label: "Light", unit: "lux", icon: Sun },
  { code: "WATER_LEVEL", label: "Water tank", unit: "%", icon: Waves },
  { code: "FEED_LEVEL", label: "Feed", unit: "%", icon: Wheat },
].map((p) => ({ ...p, value: averageOf(shownZones.value, p.code) })).filter((p) => p.value !== null).slice(0, 4));
const today = new Date().toLocaleDateString([], { weekday: "long", day: "numeric", month: "long" });
// every air-temperature sensor's last 24 h together; the chart averages them into time slots
const temperatureDay = computed(() => shownZones.value.flatMap((z) => z.sensors).filter((s) => s.type === "TEMPERATURE")
  .flatMap((s) => readings.value[s.id] ?? []).sort((a, b) => a.t - b.t));
const summary = computed(() => {
  const waiting = critical.value.length;
  return farmSummary(shownZones.value, rules.value)
    + (waiting ? ` ${waiting} critical alert${waiting > 1 ? "s are" : " is"} waiting to be acknowledged.` : "");
});

// ----- overview and today's tasks -----
const overview = computed(() => {
  const zones = shownZones.value;
  const sensors = zones.flatMap((z) => z.sensors);
  const actuators = zones.flatMap((z) => z.actuators);
  return {
    zones: zones.length,
    online: sensors.filter((s) => s.status === "ONLINE").length,
    sensors: sensors.length,
    running: actuators.filter((a) => a.status === "RUNNING").length,
    alerts: farm.value?.openAlerts ?? 0,
  };
});
const isoToday = (() => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
})();
const weekday = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"][new Date().getDay()]!;
const todayTasks = computed(() => tasks.value
  .filter((task) => task.active && task.repeatDays.includes(weekday))
  .map((task) => ({ ...task, done: task.lastRunDate === isoToday }))
  .sort((a, b) => a.runTime.localeCompare(b.runTime))
  .slice(0, 5));

// ----- analytics -----
// The last 24 h of every shown sensor and the last 7 days of each zone's device log; refreshed once a minute
// (the live values refresh every 4 s).
const readings = ref<Record<number, { t: number; v: number }[]>>({});
const zoneLogs = ref<Record<number, DeviceLog[]>>({});
const allAlerts = ref<FarmAlert[]>([]);
async function loadAnalytics() {
  const zones = shownZones.value;
  if (!zones.length) return;
  const dayAgo = new Date(Date.now() - 86400000).toISOString();
  const weekAgo = new Date(dayStart(6)).toISOString();
  const sensorIds = zones.flatMap((z) => z.sensors.map((s) => s.id));
  const [r, l, a] = await Promise.all([
    Promise.all(sensorIds.map(async (id) => {
      try {
        const rows = await api<Reading[]>(`/api/sensors/${id}/readings`, { query: { from: dayAgo } });
        return [id, rows.map((x) => ({ t: Date.parse(x.recordedAt), v: x.value }))] as const;
      } catch {
        return [id, readings.value[id] ?? []] as const;
      }
    })),
    Promise.all(zones.map(async (z) => {
      try {
        return [z.id, await api<DeviceLog[]>(`/api/zones/${z.id}/logs`, { query: { from: weekAgo } })] as const;
      } catch {
        return [z.id, zoneLogs.value[z.id] ?? []] as const;
      }
    })),
    api<FarmAlert[]>("/api/alerts", { query: { scope: showLab.value ? "ALL" : "FARM" } }).catch(() => allAlerts.value),
  ]);
  readings.value = Object.fromEntries(r);
  zoneLogs.value = Object.fromEntries(l);
  allAlerts.value = a;
}
usePolling(loadAnalytics, 60000);

// A link to /#zone-3 (old bookmarks, the map) scrolls once the zones have arrived, not before.
const route = useRoute();
watch(() => [route.hash, !!farm.value] as const, async ([hash, ready]) => {
  if (!hash || !ready) return;
  await nextTick();
  document.querySelector(hash)?.scrollIntoView({ behavior: "smooth", block: "start" });
}, { immediate: true });
watch(() => `${shownZones.value.map((z) => `${z.id}:${z.sensors.length}`).join()}|${shownZones.value.flatMap((z) => z.actuators.map((x) => x.status)).join()}`, loadAnalytics);

// With many zones only the most stressed show at first; "Show more" adds six at a time.
const pickerOpen = useState("zonePicker.open", () => false);
const zoneLimit = ref(6);
const rankedZones = computed(() => [...shownZones.value].sort((a, b) =>
  zoneHealth(b, rules.value, critical.value.some((x) => x.zoneId === b.id)).stress - zoneHealth(a, rules.value, critical.value.some((x) => x.zoneId === a.id)).stress));

// farm-wide: alerts per day by severity, and machine run time per zone
const days7 = Array.from({ length: 7 }, (_, i) => dayStart(6 - i));
const dayLabels = days7.map((d, i) => (i === 6 ? "Today" : new Date(d).toLocaleDateString([], { weekday: "short" })));
const alertsByDay = computed(() => {
  const count = (sev: string) => days7.map((d) => allAlerts.value.filter((a) => a.severity === sev
    && Date.parse(a.raisedAt) >= d && Date.parse(a.raisedAt) < d + 86400000).length);
  return [
    { name: "Critical", color: "var(--destructive)", values: count("CRITICAL") },
    { name: "Warning", color: "var(--warning)", values: count("WARNING") },
    { name: "Info", color: "var(--water)", values: count("INFO") },
  ];
});
const alertsWeek = computed(() => alertsByDay.value.reduce((s, x) => s + x.values.reduce((a, b) => a + b, 0), 0));
const runByZone = computed(() => shownZones.value.map((z, i) => {
  const runs = runsFromLogs(zoneLogs.value[z.id] ?? [], dayStart(6), Date.now(),
    new Set(z.actuators.filter((a) => a.status === "RUNNING").map((a) => a.id)));
  return { name: z.name, color: ["#6b8e23", "#e3962a", "#3f88b5", "#a07cc5", "#c46f8f", "#5f9e80"][i % 6]!,
    values: days7.map((d) => runs.reduce((s, r) => s + Math.max(0, Math.min(r.end, d + 86400000) - Math.max(r.start, d)), 0) / 60000) };
}));
function zoneInRange(zoneId: number) {
  const z = shownZones.value.find((x) => x.id === zoneId);
  const vs = (z?.sensors ?? []).map((s) => sensorStats(s, readings.value[s.id] ?? [], rules.value)?.inRange)
    .filter((v): v is number => v !== null && v !== undefined);
  return vs.length ? Math.round(vs.reduce((a, b) => a + b, 0) / vs.length) : null;
}
// today against yesterday, for the figure cards
const dayFigures = computed(() => {
  const today = dayStart(0);
  const yesterday = dayStart(1);
  const overlap = (r: { start: number; end: number }, a: number, b: number) => Math.max(0, Math.min(r.end, b) - Math.max(r.start, a)) / 60000;
  const runs = shownZones.value.flatMap((z) => runsFromLogs(zoneLogs.value[z.id] ?? [], dayStart(6), Date.now(),
    new Set(z.actuators.filter((a) => a.status === "RUNNING").map((a) => a.id))));
  const logs = shownZones.value.flatMap((z) => zoneLogs.value[z.id] ?? []).filter((l) => l.action === "ACTIVATED").map((l) => Date.parse(l.at));
  const raised = allAlerts.value.map((a) => Date.parse(a.raisedAt));
  const between = (ts: number[], a: number, b: number) => ts.filter((t) => t >= a && t < b).length;
  const now = Date.now();
  return {
    runToday: runs.reduce((s, r) => s + overlap(r, today, now), 0),
    runYesterday: runs.reduce((s, r) => s + overlap(r, yesterday, today), 0),
    startsToday: between(logs, today, now + 1), startsYesterday: between(logs, yesterday, today),
    alertsToday: between(raised, today, now + 1), alertsYesterday: between(raised, yesterday, today),
  };
});
const taskProgress = computed(() => {
  const done = todayTasks.value.filter((x) => x.done).length;
  return { done, total: todayTasks.value.length, pct: todayTasks.value.length ? Math.round((done / todayTasks.value.length) * 100) : 0 };
});
const runWeek = computed(() => runByZone.value.reduce((s, x) => s + x.values.reduce((a, b) => a + b, 0), 0));
const inRangeFarm = computed(() => {
  const all = shownZones.value.flatMap((z) => z.sensors).map((s) => sensorStats(s, readings.value[s.id] ?? [], rules.value)?.inRange)
    .filter((v): v is number => v !== null && v !== undefined);
  return all.length ? Math.round(all.reduce((a, b) => a + b, 0) / all.length) : null;
});

</script>

<template>
  <div v-if="!farm && !connectionError" class="flex flex-col gap-6" aria-busy="true" :aria-label="t('Loading')">
    <div class="grid-cols-1 grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
      <div class="skeleton h-[23rem] rounded-[1.75rem]" />
      <div class="flex flex-col gap-5"><div class="skeleton h-44 rounded-[1.75rem]" /><div class="skeleton h-44 rounded-[1.75rem]" /></div>
    </div>
    <div class="skeleton h-8 w-40" />
    <div class="grid-cols-1 grid gap-3 sm:grid-cols-3"><div v-for="i in 3" :key="i" class="skeleton h-38 rounded-2xl" /></div>
  </div>
  <div v-else class="stagger flex flex-col gap-6">
    <Alert v-if="connectionError" variant="destructive">
      <AlertDescription>{{ connectionError }}</AlertDescription>
    </Alert>

    <div class="grid-cols-1 stagger grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
      <!-- greeting -->
      <section class="relative overflow-hidden rounded-[1.75rem] border bg-linear-to-br from-card via-card to-accent/70 p-6 shadow-soft sm:p-7">
        <WheatArt class="pointer-events-none absolute top-28 right-2 hidden h-56 w-auto sm:block" />
        <div class="relative">
          <h1 class="text-3xl font-light tracking-tight sm:text-[2.4rem]">{{ t(greeting) }}, <span class="font-medium">{{ firstName }}</span></h1>
          <p class="mt-1.5 flex items-center gap-1.5 text-sm text-muted-foreground">
            <MapPin class="size-4" /> {{ farm?.name ?? "…" }}<template v-if="farm?.location"> · {{ farm.location }}</template>
          </p>
          <div class="mt-6 flex items-start gap-2">
            <span class="num text-[5.5rem] leading-[0.85] font-light"><AnimatedNumber :value="temperature" :duration="1200" /></span>
            <span class="num mt-1 text-4xl font-light">°</span>
          </div>
          <p class="mt-2 text-sm text-muted-foreground">{{ t("Average air temperature") }} · {{ today }}</p>
        </div>
        <div class="relative mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4 xl:grid-cols-2 2xl:grid-cols-4">
          <div v-for="p in pills" :key="p.code" class="flex items-center gap-3 rounded-full border bg-card/80 py-2 pr-4 pl-2 backdrop-blur">
            <span class="badge-disc size-10 shrink-0"><component :is="p.icon" class="size-[18px]" /></span>
            <div class="min-w-0 leading-tight">
              <div class="truncate text-xs text-muted-foreground">{{ t(p.label) }}</div>
              <div class="num text-lg"><AnimatedNumber :value="p.value" :decimals="p.unit === 'lux' ? 0 : 1" /><span class="ml-0.5 font-sans text-xs text-muted-foreground">{{ p.unit }}</span></div>
            </div>
          </div>
        </div>
        <div v-if="temperatureDay.length > 1" class="relative mt-5 rounded-2xl border bg-card/70 p-3 backdrop-blur">
          <div class="mb-1 flex justify-between text-xs text-muted-foreground"><span>{{ t("Air temperature, last 24 hours") }}</span><span>{{ t("all zones") }}</span></div>
          <AreaChart :points="temperatureDay" unit="°C" :height="96" />
        </div>
      </section>

      <div class="stagger flex flex-col gap-5">
        <!-- what is going on, in words -->
        <section class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <div class="flex items-center gap-2 text-xs text-muted-foreground">
            <span class="relative flex size-2"><span class="absolute inline-flex size-full animate-ping rounded-full bg-primary opacity-60" /><span class="relative size-2 rounded-full bg-primary" /></span>
            {{ t("live, refreshes every 4 seconds") }}
          </div>
          <p class="mt-2 text-[17px] leading-relaxed">“{{ summary }}”</p>
          <dl class="mt-4 grid grid-cols-4 gap-2 border-t pt-4 text-center">
            <NuxtLink to="/zones" class="rounded-xl py-1 hover:bg-muted"><dd class="num text-2xl"><AnimatedNumber :value="overview.zones" /></dd><dt class="text-[11px] text-muted-foreground">{{ t("Zones") }}</dt></NuxtLink>
            <NuxtLink to="/devices" class="rounded-xl py-1 hover:bg-muted"><dd class="num text-2xl" :class="{ 'text-warning': overview.online < overview.sensors }"><AnimatedNumber :value="overview.online" /><span class="text-base text-muted-foreground">/{{ overview.sensors }}</span></dd><dt class="text-[11px] text-muted-foreground">{{ t("Sensors online") }}</dt></NuxtLink>
            <NuxtLink to="/history" class="rounded-xl py-1 hover:bg-muted"><dd class="num text-2xl"><AnimatedNumber :value="overview.running" /></dd><dt class="text-[11px] text-muted-foreground">{{ t("Actuators running") }}</dt></NuxtLink>
            <NuxtLink to="/alerts" class="rounded-xl py-1 hover:bg-muted"><dd class="num text-2xl" :class="{ 'text-destructive': overview.alerts > 0 }"><AnimatedNumber :value="overview.alerts" /></dd><dt class="text-[11px] text-muted-foreground">{{ t("Open alerts") }}</dt></NuxtLink>
          </dl>
        </section>

        <!-- today -->
        <section class="flex-1 rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <div class="flex items-center justify-between">
            <h2 class="text-lg">{{ t("Today") }}</h2>
            <NuxtLink to="/schedules" class="hit text-xs text-muted-foreground hover:text-foreground">{{ t("Schedules") }} →</NuxtLink>
          </div>
          <div v-if="taskProgress.total" class="mt-3">
            <div class="flex justify-between text-xs text-muted-foreground"><span><span class="num text-sm text-foreground">{{ taskProgress.pct }}%</span> {{ t("done") }}</span><span>{{ taskProgress.done }}/{{ taskProgress.total }} {{ t("tasks") }}</span></div>
            <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-muted"><div class="h-full rounded-full bg-primary transition-[width] duration-700" :style="{ width: `${taskProgress.pct}%` }" /></div>
          </div>
          <ul class="mt-2 flex flex-col">
            <li v-for="task in todayTasks" :key="task.id" class="flex items-center gap-3 border-b py-2.5 last:border-0">
              <span class="flex size-5 shrink-0 items-center justify-center rounded-md border" :class="task.done ? 'border-primary bg-primary text-primary-foreground' : ''">
                <Check v-if="task.done" class="size-3.5" />
              </span>
              <CalendarClock class="size-4 shrink-0 text-muted-foreground" />
              <span class="min-w-0 flex-1 truncate text-sm" :class="{ 'text-muted-foreground line-through': task.done }">{{ task.name }} <span class="text-muted-foreground">· {{ task.zoneName }}</span></span>
              <span class="num shrink-0 text-sm text-muted-foreground">{{ task.runTime.slice(0, 5) }}</span>
            </li>
            <li v-if="!todayTasks.length" class="py-3 text-sm text-muted-foreground">{{ t("Nothing scheduled for today.") }}</li>
          </ul>
        </section>
      </div>
    </div>

    <!-- today's figures, against yesterday -->
    <div v-if="shownZones.length" class="stagger grid grid-cols-2 gap-4 xl:grid-cols-4">
      <StatCard hero :label="t('Readings in range')" :value="inRangeFarm" unit="%" :icon="Gauge" note="last 24 hours, every sensor" to="/history" />
      <StatCard :label="t('Alerts today')" :value="dayFigures.alertsToday" :delta="dayFigures.alertsToday - dayFigures.alertsYesterday" up-is-bad
        :icon="BellRing" to="/alerts" :tone="dayFigures.alertsToday > dayFigures.alertsYesterday ? 'destructive' : ''" />
      <StatCard :label="t('Run time today')" :value="Math.round(dayFigures.runToday)" unit="min" :delta="Math.round(dayFigures.runToday - dayFigures.runYesterday)"
        up-is-bad :icon="Timer" to="/history" />
      <StatCard :label="t('Machine starts today')" :value="dayFigures.startsToday" :delta="dayFigures.startsToday - dayFigures.startsYesterday"
        up-is-bad :icon="Power" to="/history" />
    </div>

    <!-- the farm map -->
    <section v-if="shownZones.length">
      <div class="mb-3 flex flex-wrap items-end justify-between gap-2">
        <h2 class="text-2xl">{{ t("Farm map") }}</h2>
        <label v-if="labZoneCount" class="flex items-center gap-2 text-sm text-muted-foreground">
          <input v-model="showLab" type="checkbox" class="size-4 accent-[var(--primary)]">
          Show Farm Lab test zones ({{ labZoneCount }})
        </label>
      </div>
      <FarmMap :zones="shownZones" :rules="rules" :critical="critical" @changed="load" />
    </section>

    <Card v-if="farm && farm.zones.length === 0">
      <CardHeader>
        <CardTitle>Set up your farm</CardTitle>
        <CardDescription>
          No zones yet. The Farm Owner adds zones, then the Technician registers sensors and actuators in each zone.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button as-child><NuxtLink to="/zones">Go to Zones &amp; devices</NuxtLink></Button>
      </CardContent>
    </Card>

    <!-- what needs a person -->
    <div v-if="shownZones.length" class="grid-cols-1 stagger grid gap-5 lg:grid-cols-2">
      <AlertSummary :alerts="allAlerts" :zones="farm?.zones ?? []" />
      <AttentionList :zones="shownZones" />
    </div>

    <!-- the last 7 days across the farm -->
    <section v-if="shownZones.length">
      <h2 class="mb-3 text-2xl">{{ t("Last 7 days") }}</h2>
      <div class="grid-cols-1 stagger grid gap-5 lg:grid-cols-3">
        <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <div class="flex items-baseline justify-between">
            <h3 class="font-medium">{{ t("Alerts") }}</h3>
            <span class="num text-2xl"><AnimatedNumber :value="alertsWeek" /></span>
          </div>
          <p class="mb-4 text-xs text-muted-foreground">{{ t("raised per day, by severity") }}</p>
          <BarChart :labels="dayLabels" :series="alertsByDay" :height="130" />
        </div>
        <div class="rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <div class="flex items-baseline justify-between">
            <h3 class="font-medium">{{ t("Machine run time") }}</h3>
            <span class="num text-2xl"><AnimatedNumber :value="runWeek" /><span class="ml-0.5 font-sans text-xs text-muted-foreground">min</span></span>
          </div>
          <p class="mb-4 text-xs text-muted-foreground">{{ t("pumps, fans, heaters... per day, by zone") }}</p>
          <BarChart :labels="dayLabels" :series="runByZone" unit=" min" :height="130" />
        </div>
        <div class="flex flex-col rounded-[1.75rem] border bg-card p-5 shadow-soft">
          <h3 class="font-medium">{{ t("Readings in range") }}</h3>
          <p class="text-xs text-muted-foreground">{{ t("share of the last 24 hours inside the rule limits") }}</p>
          <div class="my-auto flex items-center justify-center py-3">
            <RingGauge :value="inRangeFarm" />
          </div>
          <ul class="flex flex-col gap-1.5 text-sm">
            <li v-for="z in shownZones" :key="z.id" class="flex items-center gap-2">
              <span class="min-w-0 flex-1 truncate text-muted-foreground">{{ z.name }}</span>
              <span class="h-1.5 w-24 overflow-hidden rounded-full bg-muted">
                <span class="block h-full rounded-full bg-primary transition-[width] duration-700"
                  :style="{ width: `${zoneInRange(z.id) ?? 0}%` }" />
              </span>
              <span class="num w-10 text-right">{{ zoneInRange(z.id) === null ? "–" : `${zoneInRange(z.id)}%` }}</span>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- every zone in detail -->
    <section v-if="shownZones.length">
      <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 class="text-2xl">{{ t("Zones") }} <span class="text-base text-muted-foreground">· {{ shownZones.length }}</span></h2>
        <span class="text-sm text-muted-foreground">{{ t("most stressed first") }} · <button type="button" class="hit underline-offset-2 hover:text-foreground hover:underline" @click="pickerOpen = true">{{ t("find a zone") }}</button></span>
      </div>
      <div class="grid-cols-1 stagger grid gap-5 xl:grid-cols-2">
        <ZoneCard v-for="zone in rankedZones.slice(0, zoneLimit)" :key="zone.id" :zone="zone" :rules="rules" :readings="readings"
          :logs="zoneLogs[zone.id] ?? []" :critical-open="critical.some((a) => a.zoneId === zone.id)" @changed="load" />
      </div>
      <div v-if="rankedZones.length > zoneLimit" class="mt-4 flex justify-center">
        <button type="button" class="round-btn w-auto px-5 text-sm" @click="zoneLimit += 6">
          {{ t("Show more zones") }} <span class="ml-1.5 text-muted-foreground">({{ rankedZones.length - zoneLimit }} {{ t("left") }})</span>
        </button>
      </div>
    </section>
  </div>
</template>
