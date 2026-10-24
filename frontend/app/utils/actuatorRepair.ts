// The technician's actions on an actuator (Deliverable 9 state machine), shared by "Zones & devices" and "Devices".
import type { ActuatorStatus, LifecycleAction } from "./types";

export const actuatorOptions: Partial<Record<ActuatorStatus, { action: LifecycleAction; label: string; body?: { testPassed?: boolean } }[]>> = {
  IDLE: [{ action: "FAULT", label: "Report fault" }, { action: "OFFLINE", label: "Mark offline" }],
  RUNNING: [{ action: "FAULT", label: "Report fault" }, { action: "OFFLINE", label: "Mark offline" }],
  OFFLINE: [{ action: "RESTORE", label: "Heartbeat restored" }, { action: "START_REPAIR", label: "Start repair" }],
  FAULT: [{ action: "START_REPAIR", label: "Start repair" }],
  MAINTENANCE: [
    { action: "COMPLETE_REPAIR", label: "Repair done (test passed)", body: { testPassed: true } },
    { action: "COMPLETE_REPAIR", label: "Test failed", body: { testPassed: false } },
    { action: "DECOMMISSION", label: "Decommission" },
  ],
};

export const actuatorDone: Record<LifecycleAction, string> = {
  FAULT: "fault reported",
  OFFLINE: "marked offline",
  RESTORE: "back online",
  START_REPAIR: "repair started",
  COMPLETE_REPAIR: "repair finished",
  DECOMMISSION: "decommissioned",
};
