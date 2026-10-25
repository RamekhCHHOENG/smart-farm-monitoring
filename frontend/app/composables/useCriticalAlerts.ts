import type { Alert } from "~/utils/types";

const SOUND_KEY = "smartfarm.alertSound";
/**
 * Watches open alerts on every page. Every open CRITICAL alert is listed behind the alert pill in the top bar.
 * A new one (or the same problem happening again) pops up as a card in the corner, and also beeps and shows a
 * system pop-up when "Alert sound" is on, so a worker notices even when looking at another tab.
 */
export function useCriticalAlerts() {
  const api = useApi();
  const { token } = useAuth();
  const openAlerts = useState<number>("openAlerts", () => 0);
  const critical = useState<Alert[]>("criticalOpen", () => []);
  const latest = useState<Alert | null>("criticalAlert", () => null);
  const soundOn = useState<boolean>("alertSound", () => {
    try {
      return localStorage.getItem(SOUND_KEY) === "on";
    } catch {
      return false;
    }
  });

  // id -> repeat count already seen; null until the first load so old alerts do not ring
  let seen: Map<number, number> | null = null;
  let timer: ReturnType<typeof setInterval> | undefined;

  async function check() {
    if (!token.value) return;
    let alerts: Alert[];
    try {
      alerts = await api<Alert[]>("/api/alerts", { query: { status: "OPEN" } });
    } catch {
      return; // the page itself shows connection problems
    }
    openAlerts.value = alerts.length;
    critical.value = alerts.filter((a) => a.severity === "CRITICAL")
      .sort((a, b) => b.lastRaisedAt.localeCompare(a.lastRaisedAt));
    // the first load only fills the list: alerts already open before signing in do not pop up again
    const fresh = seen ? critical.value.filter((a) => (seen!.get(a.id) ?? 0) < a.repeatCount) : [];
    seen = new Map(alerts.map((a) => [a.id, a.repeatCount]));
    if (fresh.length) {
      latest.value = fresh[0]!;
      if (soundOn.value) {
        beep();
        systemNotification(fresh[0]!);
      }
    } else if (latest.value && !critical.value.some((a) => a.id === latest.value!.id)) {
      latest.value = null; // acknowledged by someone
    }
  }

  function dismiss() {
    latest.value = null;
  }

  async function acknowledge(id: number) {
    await api(`/api/alerts/${id}/acknowledge`, { method: "POST" });
    if (latest.value?.id === id) latest.value = null;
    await check();
  }

  async function setSound(on: boolean) {
    soundOn.value = on;
    try {
      localStorage.setItem(SOUND_KEY, on ? "on" : "off");
    } catch {
      // ignore blocked storage
    }
    if (on) {
      // Asked after a click, as browsers require; the sound itself works without it.
      if ("Notification" in window && Notification.permission === "default") {
        await Notification.requestPermission().catch(() => undefined);
      }
      beep();
    }
  }

  function start() {
    check();
    timer = setInterval(check, 8000);
  }

  function stop() {
    clearInterval(timer);
  }

  return { latest, critical, soundOn, setSound, dismiss, acknowledge, start, stop };
}

let audio: AudioContext | null = null;

/** Two short tones; no sound file needed. */
function beep() {
  try {
    audio ??= new AudioContext();
    [0, 0.25].forEach((offset) => {
      const osc = audio!.createOscillator();
      const gain = audio!.createGain();
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.2, audio!.currentTime + offset);
      gain.gain.exponentialRampToValueAtTime(0.001, audio!.currentTime + offset + 0.2);
      osc.connect(gain).connect(audio!.destination);
      osc.start(audio!.currentTime + offset);
      osc.stop(audio!.currentTime + offset + 0.2);
    });
  } catch {
    // audio blocked until the person interacts with the page
  }
}

function systemNotification(alert: Alert) {
  if ("Notification" in window && Notification.permission === "granted") {
    try {
      new Notification(`Smart Farm: ${t("CRITICAL")}`, { body: alert.message, tag: `alert-${alert.id}` });
    } catch {
      // some mobile browsers only allow notifications from a service worker
    }
  }
}
