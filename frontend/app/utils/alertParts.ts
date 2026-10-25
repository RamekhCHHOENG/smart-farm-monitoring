import type { Alert } from "~/utils/types";

/** The parts of an alert message, so it can be laid out instead of shown as one long sentence. */
export interface AlertParts {
  /** "Vegetable Bed A" */
  zone: string | null;
  /** "Soil moisture A1 reads 29.7% (below 30.0%)" */
  problem: string;
  /** "Soil moisture A1", when the message is about a reading */
  subject: string | null;
  /** "29.7%" */
  value: string | null;
  /** "below 30.0%" */
  limit: string | null;
  /** "Watering" */
  actionLabel: string | null;
  /** "Water pump A started until 60% (max 10 min)" */
  action: string | null;
}

// Messages look like "Zone: problem - Action: what the farm did" (see AutomationRule.format on the server).
export function alertParts(a: Pick<Alert, "message">): AlertParts {
  let text = a.message.trim();
  let zone: string | null = null;
  const zoneMatch = /^([^:–-]{1,60}):\s+(.+)$/s.exec(text);
  if (zoneMatch) {
    zone = zoneMatch[1]!.trim();
    text = zoneMatch[2]!;
  }
  const [problem, ...rest] = text.split(" - ");
  const actionText = rest.join(" - ").trim() || null;
  let actionLabel: string | null = null;
  let action = actionText;
  const labelMatch = actionText ? /^([A-Z][\w ]{1,24}):\s+(.+)$/s.exec(actionText) : null;
  if (labelMatch) {
    actionLabel = labelMatch[1]!;
    action = labelMatch[2]!;
  }
  const reading = /^(.+?) reads (\S+?)\s*\((below|above) ([^)]+)\)/.exec(problem!);
  return {
    zone,
    problem: problem!.trim(),
    subject: reading ? reading[1]! : null,
    value: reading ? reading[2]! : null,
    limit: reading ? `${reading[3]} ${reading[4]}` : null,
    actionLabel,
    action,
  };
}

/** A short tag for the kind of problem (Low, High, Fault…), coloured by severity. */
export function alertTag(a: Pick<Alert, "message" | "severity">) {
  const m = a.message.toLowerCase();
  const p = alertParts(a);
  const label = m.includes("fault") ? "Fault" : m.includes("offline") ? "Offline" : m.includes("did not") || m.includes("no effect") ? "No effect"
    : p.limit?.startsWith("below") ? "Low" : p.limit?.startsWith("above") ? "High" : m.includes("could not") ? "Failed" : a.severity === "INFO" ? "Info" : "Check";
  const tone = a.severity === "CRITICAL" ? "bg-destructive/12 text-destructive" : a.severity === "WARNING" ? "bg-warning/18 text-foreground" : "bg-water/12 text-water";
  return { label, tone };
}

/** The sensor an alert is about, as it reads right now, and whether it is back inside the limit the alert was about.
 *  An alert stays open until someone acknowledges it, so "29.7% below 30%" can be old news by the time it is read. */
export function alertNow(a: Pick<Alert, "message" | "zoneId">,
  zones: { id: number; sensors: { name: string; unit: string; latest: { value: number } | null }[] }[]) {
  const p = alertParts(a);
  if (!p.subject || !p.limit) return null;
  const sensor = zones.find((z) => z.id === a.zoneId)?.sensors.find((s) => s.name === p.subject);
  if (!sensor?.latest) return null;
  const m = /^(below|above)\s+(-?[\d.]+)/.exec(p.limit);
  if (!m) return null;
  const limit = Number(m[2]);
  const v = sensor.latest.value;
  return { value: v, unit: sensor.unit, recovered: m[1] === "below" ? v >= limit : v <= limit };
}
