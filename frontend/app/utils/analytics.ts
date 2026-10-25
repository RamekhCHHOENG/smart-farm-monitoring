import type { Actuator, DeviceLog, Rule, Sensor } from "~/utils/types";

// Figures for the dashboard's analytics, worked out in the browser from data the server already keeps:
// sensor readings (up to 500 per range) and the device log (every start and stop, with its time).

export interface Run { actuatorId: number; start: number; end: number }

/** Turn ACTIVATED / DEACTIVATED log lines into runs. A run still going ends now; one begun before the window starts at it. */
export function runsFromLogs(logs: DeviceLog[], from: number, now: number, running: Set<number>): Run[] {
  const sorted = [...logs].sort((a, b) => a.at.localeCompare(b.at));
  const open = new Map<number, number>();
  const runs: Run[] = [];
  for (const l of sorted) {
    const t = Date.parse(l.at);
    if (l.action === "ACTIVATED") {
      if (!open.has(l.actuatorId)) open.set(l.actuatorId, t);
    } else if (l.action === "DEACTIVATED" || l.action === "FAULT" || l.action === "OFFLINE" || l.action === "DECOMMISSION") {
      const start = open.get(l.actuatorId) ?? (l.action === "DEACTIVATED" ? from : null);
      if (start !== null) runs.push({ actuatorId: l.actuatorId, start, end: t });
      open.delete(l.actuatorId);
    }
  }
  for (const [id, start] of open) if (running.has(id)) runs.push({ actuatorId: id, start, end: now });
  return runs;
}

export const minutes = (runs: Run[]) => runs.reduce((s, r) => s + (r.end - r.start), 0) / 60000;

/** Midnight (browser time) of the day `daysAgo` days back. */
export function dayStart(daysAgo: number) {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - daysAgo);
  return d.getTime();
}

/** Minutes each actuator ran on each of the last 7 days (oldest first), cutting runs at midnight. */
export function runMinutesByDay(runs: Run[], actuators: Actuator[]) {
  const days = Array.from({ length: 7 }, (_, i) => dayStart(6 - i));
  const labels = days.map((d, i) => (i === 6 ? "Today" : new Date(d).toLocaleDateString([], { weekday: "short" })));
  const series = actuators.map((a) => ({
    actuator: a,
    values: days.map((d) => {
      const end = d + 86400000;
      return runs.filter((r) => r.actuatorId === a.id)
        .reduce((s, r) => s + Math.max(0, Math.min(r.end, end) - Math.max(r.start, d)), 0) / 60000;
    }),
  }));
  return { labels, series };
}

export interface SensorStats {
  min: number; avg: number; max: number;
  /** change against the reading about an hour ago */
  delta: number | null;
  /** share of readings inside every active rule limit, 0-100, or null without rules */
  inRange: number | null;
}

export function sensorStats(s: Sensor, points: { t: number; v: number }[], rules: Rule[]): SensorStats | null {
  if (!points.length) return null;
  const vs = points.map((p) => p.v);
  const hourAgo = Date.now() - 3600000;
  const past = [...points].reverse().find((p) => p.t <= hourAgo);
  const latest = s.latest?.value ?? vs[vs.length - 1]!;
  const mine = rules.filter((r) => r.active && r.sensorId === s.id);
  const ok = (v: number) => mine.every((r) => (r.condition === "BELOW" ? v >= r.threshold : v <= r.threshold));
  return {
    min: Math.min(...vs),
    avg: vs.reduce((a, b) => a + b, 0) / vs.length,
    max: Math.max(...vs),
    delta: past ? latest - past.v : null,
    inRange: mine.length ? Math.round((vs.filter(ok).length / vs.length) * 100) : null,
  };
}

/** Colours for actuators in charts, by kind, so a pump is always blue and a fan always green. */
export function actuatorColor(type: string, i = 0) {
  const known: Record<string, string> = {
    WATER_PUMP: "#3f88b5", WATER_VALVE: "#5aa6c9", FAN: "#6b8e23", HEATER: "#e0602b", MISTER: "#8fbfd9",
    LIGHT: "#e8b440", FEEDER: "#c08a3e",
  };
  return known[type] ?? ["#a07cc5", "#7a8c99", "#c46f8f", "#5f9e80"][i % 4]!;
}
