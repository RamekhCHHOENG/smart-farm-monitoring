import { useIntervalFn } from "@vueuse/core";
import type { MaybeRefOrGetter } from "vue";
import type { DeviceLog, Rule, Zone } from "~/utils/types";
import { zoneHealth } from "~/utils/farmHealth";
import { actuatorColor, dayStart, minutes, runMinutesByDay, runsFromLogs, sensorStats } from "~/utils/analytics";

/**
 * Everything worked out about one zone from its live state, its rules, the last 24 h of readings and the last
 * 7 days of its device log: stress, today's figures against yesterday, each sensor's stats, each machine's runs.
 * Shared by the dashboard's zone cards and the zone page.
 */
export function useZoneFigures(opts: {
  zone: MaybeRefOrGetter<Zone | null | undefined>;
  rules: MaybeRefOrGetter<Rule[]>;
  readings: MaybeRefOrGetter<Record<number, { t: number; v: number }[]>>;
  logs: MaybeRefOrGetter<DeviceLog[]>;
  criticalOpen?: MaybeRefOrGetter<boolean>;
}) {
  const zone = computed(() => toValue(opts.zone) ?? null);
  const rules = computed(() => toValue(opts.rules));
  const readings = computed(() => toValue(opts.readings));
  const logs = computed(() => toValue(opts.logs));

  const now = ref(Date.now());
  useIntervalFn(() => (now.value = Date.now()), 30000);
  const dayAgo = computed(() => now.value - 86400000);

  const health = computed(() => (zone.value ? zoneHealth(zone.value, rules.value, toValue(opts.criticalOpen) ?? false) : null));
  const actuators = computed(() => (zone.value?.actuators ?? []).filter((a) => a.status !== "DECOMMISSIONED"));
  const runs = computed(() => runsFromLogs(logs.value, dayStart(6), now.value,
    new Set(actuators.value.filter((a) => a.status === "RUNNING").map((a) => a.id))));

  const kpis = computed(() => {
    const today = dayStart(0);
    const yesterday = today - 86400000;
    const sensors = zone.value?.sensors ?? [];
    const overlap = (a: number, b: number) => runs.value.reduce((s, r) => s + Math.max(0, Math.min(r.end, b) - Math.max(r.start, a)), 0) / 60000;
    const starts = (a: number, b: number) => logs.value.filter((l) => l.action === "ACTIVATED" && Date.parse(l.at) >= a && Date.parse(l.at) < b).length;
    return {
      online: sensors.filter((s) => s.status === "ONLINE").length,
      sensors: sensors.length,
      runMin: overlap(today, now.value),
      runYesterday: overlap(yesterday, today),
      starts: starts(today, now.value + 1),
      startsYesterday: starts(yesterday, today),
    };
  });

  const limitsOf = (sensorId: number) => rules.value.filter((r) => r.active && r.sensorId === sensorId)
    .map((r) => ({ value: r.threshold, label: `${r.condition === "BELOW" ? "<" : ">"} ${r.threshold}` }));
  const statsOf = (sensorId: number) => {
    const s = zone.value?.sensors.find((x) => x.id === sensorId);
    return s ? sensorStats(s, readings.value[sensorId] ?? [], rules.value) : null;
  };
  const alarmOf = (sensorId: number) => health.value?.breached.some((b) => b.sensor.id === sensorId) ?? false;
  const runsOf = (actuatorId: number) => runs.value.filter((r) => r.actuatorId === actuatorId && r.end > dayAgo.value);
  const colorOf = (actuatorId: number) => {
    const i = actuators.value.findIndex((a) => a.id === actuatorId);
    return actuatorColor(actuators.value[i]?.type ?? "", Math.max(0, i));
  };

  const week = computed(() => {
    const { labels, series } = runMinutesByDay(runs.value, actuators.value);
    return { labels, series: series.map((s) => ({ name: s.actuator.name, color: colorOf(s.actuator.id), values: s.values })) };
  });
  const weekTotal = computed(() => week.value.series.reduce((s, x) => s + x.values.reduce((a, b) => a + b, 0), 0));

  return { now, dayAgo, health, actuators, runs, kpis, limitsOf, statsOf, alarmOf, runsOf, colorOf, week, weekTotal, minutes };
}

/** How a zone looks everywhere: its colour dot, text colour and map field texture. */
export function zoneLook(z: { type: string; name: string; detail?: string | null }) {
  if (/greenhouse/i.test(`${z.name} ${z.detail ?? ""}`)) return { dot: "bg-greenhouse", text: "text-greenhouse", field: "field-greenhouse", kind: "Greenhouse" };
  return z.type === "POULTRY"
    ? { dot: "bg-poultry", text: "text-poultry", field: "field-poultry", kind: "Poultry house" }
    : { dot: "bg-crop", text: "text-crop", field: "field-crop", kind: "Crop bed" };
}

export const fmtMinutes = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} h ${Math.round(m % 60)} min` : `${Math.round(m)} min`);
