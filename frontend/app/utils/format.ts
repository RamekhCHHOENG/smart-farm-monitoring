export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return "never";
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return `${Math.max(seconds, 0)}s ago`;
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  return `${Math.round(seconds / 3600)} h ago`;
}

export function minutesLeft(iso: string | null): string {
  if (!iso) return "";
  const s = Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")} left`;
}

/** "Today 17:00", "Tomorrow 07:00" or "Thu 07:00" for a farm-local date-time like 2026-10-22T07:00:00. */
export function formatNextRun(local: string | null): string {
  if (!local) return "Paused";
  const [date, time] = local.split("T");
  const hhmm = (time ?? "").slice(0, 5);
  const day = new Date(`${date}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((day.getTime() - today.getTime()) / 86400000);
  if (diff === 0) return `Today ${hhmm}`;
  if (diff === 1) return `Tomorrow ${hhmm}`;
  return `${day.toLocaleDateString(undefined, { weekday: "short" })} ${hhmm}`;
}

/** Converts a value from a <input type="datetime-local"> to an ISO instant. */
export function localInputToIso(value: string): string {
  return new Date(value).toISOString();
}

export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
