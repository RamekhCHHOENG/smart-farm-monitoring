// The technician's repair flow for a sensor, shared by "Zones & devices" and the Farm Lab:
// report a fault -> start the repair -> complete it once the sensor has sent a valid reading.
import type { Sensor, SensorAction, SensorStatus } from "./types";

/** Buttons offered for a sensor in each status. */
export const sensorOptions: Record<SensorStatus, { action: SensorAction; label: string }[]> = {
  ONLINE: [{ action: "FAULT", label: "Report fault" }],
  OFFLINE: [{ action: "START_REPAIR", label: "Start repair" }, { action: "FAULT", label: "Report fault" }],
  FAULT: [{ action: "START_REPAIR", label: "Start repair" }],
  MAINTENANCE: [{ action: "COMPLETE_REPAIR", label: "Complete repair (test)" }],
};

export const sensorDone: Record<SensorAction, string> = {
  FAULT: "reported faulty, its readings are ignored",
  START_REPAIR: "repair started, waiting for a valid reading",
  COMPLETE_REPAIR: "repaired and back in service",
};

/** A sensor that sends impossible values shows a warning while it is otherwise online. */
export const sensorBadge = (s: Sensor) => (s.status === "ONLINE" && s.suspect ? "WARNING" : s.status);

/** Has the sensor sent a valid reading since the repair began? Then the test will pass. */
export const testReadingArrived = (s: Sensor) =>
  !s.suspect && !!s.lastSeenAt && !!s.repairStartedAt && Date.parse(s.lastSeenAt) >= Date.parse(s.repairStartedAt);

/** What the technician should know or do next, in one sentence. */
export function sensorNote(s: Sensor) {
  switch (s.status) {
    case "OFFLINE": return "No reading lately. Check power, cable and Wi-Fi, then start a repair.";
    case "FAULT": return "Readings ignored and its rules paused until it is repaired.";
    case "MAINTENANCE": return testReadingArrived(s)
      ? "A valid reading arrived. Complete the repair to put it back in service."
      : "Waiting for a valid reading. Its rules stay paused.";
    default: return s.suspect ? "The last reading was impossible. Check the wiring." : "";
  }
}
