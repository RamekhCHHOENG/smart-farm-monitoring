import type { Rule, Sensor, Zone } from "~/utils/types";

// How a zone is doing, worked out from its sensors and the farm's own rules (no guessing):
// a reading past a rule's limit is full stress, close to a limit is some stress, a broken or silent sensor is unknown.

export type HealthLevel = "Low" | "Medium" | "High";

export interface SensorStress {
  sensor: Sensor;
  stress: number;
  rule: Rule | null;
  breached: boolean;
}

export function sensorStress(s: Sensor, rules: Rule[]): SensorStress {
  if (s.status === "FAULT" || s.status === "OFFLINE") return { sensor: s, stress: 50, rule: null, breached: false };
  if (s.status === "MAINTENANCE" || !s.latest) return { sensor: s, stress: 0, rule: null, breached: false };
  const v = s.latest.value;
  const span = s.maxValid - s.minValid || 1;
  let worst: SensorStress = { sensor: s, stress: 0, rule: null, breached: false };
  for (const r of rules) {
    if (!r.active || r.sensorId !== s.id) continue;
    const past = r.condition === "BELOW" ? v < r.threshold : v > r.threshold;
    // within 15% of the range from the limit counts as getting close
    const gap = Math.abs(v - r.threshold) / span;
    const stress = past ? 100 : Math.round(Math.max(0, 1 - gap / 0.15) * 60);
    if (stress > worst.stress) worst = { sensor: s, stress, rule: r, breached: past };
  }
  return worst;
}

export interface ZoneHealth {
  stress: number;
  level: HealthLevel;
  worst: SensorStress | null;
  breached: SensorStress[];
}

export function zoneHealth(z: Zone, rules: Rule[], criticalOpen = false): ZoneHealth {
  const all = z.sensors.map((s) => sensorStress(s, rules));
  const worst = all.reduce<SensorStress | null>((w, x) => (!w || x.stress > w.stress ? x : w), null);
  const stress = Math.max(worst?.stress ?? 0, criticalOpen ? 80 : 0);
  return { stress, level: stress >= 70 ? "High" : stress >= 30 ? "Medium" : "Low", worst, breached: all.filter((x) => x.breached) };
}

/** Average of every working sensor of one measurement across the given zones, or null. */
export function averageOf(zones: Zone[], code: string) {
  const values = zones.flatMap((z) => z.sensors)
    .filter((s) => s.type === code && s.latest && s.status !== "FAULT" && s.status !== "MAINTENANCE")
    .map((s) => s.latest!.value);
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}

/** One or two plain sentences about the farm right now, built from the readings and what is running. */
export function farmSummary(zones: Zone[], rules: Rule[]) {
  if (!zones.length) return "No zones yet.";
  const notes: string[] = [];
  for (const z of zones) {
    const h = zoneHealth(z, rules);
    for (const b of h.breached.slice(0, 1)) {
      const s = b.sensor;
      const running = z.actuators.filter((a) => a.status === "RUNNING").map((a) => a.name);
      notes.push(`${z.name}: ${s.name} is ${b.rule!.condition === "BELOW" ? "below" : "above"} its limit (${s.latest!.value.toFixed(1)}${s.unit})${running.length ? `, ${running.join(" and ")} ${running.length > 1 ? "are" : "is"} on it` : ", nothing is running yet"}.`);
    }
  }
  const silent = zones.flatMap((z) => z.sensors).filter((s) => s.status === "OFFLINE" || s.status === "FAULT").length;
  if (!notes.length) {
    notes.push(`Conditions are stable across ${zones.length} zone${zones.length > 1 ? "s" : ""}: every reading is inside its limits.`);
  }
  if (silent) notes.push(`${silent} sensor${silent > 1 ? "s are" : " is"} offline or faulty.`);
  return notes.slice(0, 3).join(" ");
}
