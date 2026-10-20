// Mirrors the backend DTOs in com.smartfarm.web.Dtos

export type Role = "OWNER" | "WORKER" | "TECHNICIAN";
export type SensorType = "SOIL_MOISTURE" | "TEMPERATURE" | "FEED_LEVEL";
export type ActuatorType = "WATER_PUMP" | "FAN" | "FEEDER";
export type ActuatorStatus = "IDLE" | "RUNNING" | "FAULT" | "OFFLINE" | "MAINTENANCE" | "DECOMMISSIONED";
export type Condition = "ABOVE" | "BELOW";
export type Severity = "INFO" | "WARNING" | "CRITICAL";
export type AlertStatus = "OPEN" | "ACKNOWLEDGED";
export type LifecycleAction = "FAULT" | "OFFLINE" | "RESTORE" | "START_REPAIR" | "COMPLETE_REPAIR" | "DECOMMISSION";

export interface User {
  id: number;
  name: string;
  role: Role;
}

export interface Reading {
  id: number;
  sensorId: number;
  value: number;
  recordedAt: string;
}

export interface Sensor {
  id: number;
  zoneId: number;
  name: string;
  type: SensorType;
  unit: string;
  minValid: number;
  maxValid: number;
  simulated: boolean;
  online: boolean;
  suspect: boolean;
  lastSeenAt: string | null;
  latest: Reading | null;
}

export interface Actuator {
  id: number;
  zoneId: number;
  name: string;
  type: ActuatorType;
  status: ActuatorStatus;
  maxRunMinutes: number;
  runningUntil: string | null;
}

export interface Zone {
  id: number;
  name: string;
  type: "CROP" | "POULTRY";
  detail: string;
  cropName: string | null;
  birdCount: number | null;
  sensors: Sensor[];
  actuators: Actuator[];
}

export interface Farm {
  id: number;
  name: string;
  location: string;
  openAlerts: number;
  zones: Zone[];
}

export interface Rule {
  id: number;
  name: string;
  sensorId: number;
  sensorName: string;
  zoneId: number;
  zoneName: string;
  condition: Condition;
  threshold: number;
  unit: string;
  actuatorId: number | null;
  actuatorName: string | null;
  durationMin: number;
  severity: Severity;
  active: boolean;
  cooldownMinutes: number;
  lastTriggeredAt: string | null;
}

export interface Task {
  id: number;
  name: string;
  actuatorId: number;
  actuatorName: string;
  zoneId: number;
  zoneName: string;
  runTime: string;
  repeatDays: string[];
  durationMin: number;
  active: boolean;
  lastRunDate: string | null;
}

export interface Alert {
  id: number;
  message: string;
  severity: Severity;
  status: AlertStatus;
  raisedAt: string;
  zoneId: number | null;
  ruleId: number | null;
  delivered: boolean;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
}

export interface DeviceLog {
  id: number;
  zoneId: number;
  actuatorId: number;
  actuatorName: string;
  action: string;
  source: string;
  userName: string | null;
  detail: string | null;
  at: string;
}

export const sensorLabels: Record<SensorType, string> = {
  SOIL_MOISTURE: "Soil moisture",
  TEMPERATURE: "Temperature",
  FEED_LEVEL: "Feed level",
};

export const actuatorLabels: Record<ActuatorType, string> = {
  WATER_PUMP: "Water pump",
  FAN: "Fan",
  FEEDER: "Feeder",
};

export const roleLabels: Record<Role, string> = {
  OWNER: "Farm Owner",
  WORKER: "Farm Worker",
  TECHNICIAN: "Technician",
};
