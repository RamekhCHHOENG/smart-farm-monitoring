// Mirrors the backend DTOs in com.smartfarm.web.Dtos

export type Role = "OWNER" | "WORKER" | "TECHNICIAN";
export type SensorType = "SOIL_MOISTURE" | "TEMPERATURE" | "FEED_LEVEL" | "HUMIDITY" | "LIGHT_LEVEL" | "WATER_LEVEL";
export type ActuatorType = "WATER_PUMP" | "FAN" | "FEEDER" | "HEATER" | "MISTER" | "LIGHT" | "WATER_VALVE";
export type SensorStatus = "ONLINE" | "OFFLINE" | "FAULT" | "MAINTENANCE";
export type ActuatorStatus = "IDLE" | "RUNNING" | "FAULT" | "OFFLINE" | "MAINTENANCE" | "DECOMMISSIONED";
export type Condition = "ABOVE" | "BELOW";
export type Severity = "INFO" | "WARNING" | "CRITICAL";
export type AlertStatus = "OPEN" | "ACKNOWLEDGED";
export type LifecycleAction = "FAULT" | "OFFLINE" | "RESTORE" | "START_REPAIR" | "COMPLETE_REPAIR" | "DECOMMISSION";
export type SensorAction = "FAULT" | "START_REPAIR" | "COMPLETE_REPAIR";

export interface User {
  id: number;
  username: string;
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
  /** ONLINE / OFFLINE follow the signal; FAULT / MAINTENANCE are set by a technician */
  status: SensorStatus;
  /** set while the sensor is in MAINTENANCE */
  repairStartedAt: string | null;
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
  /** set while a rule's run is going */
  startedByRuleId: number | null;
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
  timeZone: string;
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
  /** null = run for a fixed time; a value = keep the range (stop when the reading is back here) */
  stopAt: number | null;
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
  nextRun: string | null;
}

export interface Alert {
  id: number;
  message: string;
  severity: Severity;
  status: AlertStatus;
  raisedAt: string;
  repeatCount: number;
  lastRaisedAt: string;
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
  HUMIDITY: "Air humidity",
  LIGHT_LEVEL: "Light level",
  WATER_LEVEL: "Water tank level",
};

export const actuatorLabels: Record<ActuatorType, string> = {
  WATER_PUMP: "Water pump",
  FAN: "Fan",
  FEEDER: "Feeder",
  HEATER: "Heater",
  MISTER: "Mister",
  LIGHT: "Light",
  WATER_VALVE: "Tank fill valve",
};

/** One line under the type picker, so a technician knows what each kind is for. */
export const sensorHints: Record<SensorType, string> = {
  SOIL_MOISTURE: "Soil water in % (capacitive probe). Usually drives a water pump.",
  TEMPERATURE: "Air temperature in °C (DHT22). Drives a fan when hot or a heater when cold.",
  FEED_LEVEL: "How full a feed tray is, in %. Usually drives a feeder.",
  HUMIDITY: "Air humidity in % (the DHT22 also reports it). Drives a mister when dry or a fan when damp.",
  LIGHT_LEVEL: "Light in lux (BH1750). Drives a light when it gets dark.",
  WATER_LEVEL: "How full a water tank is, in %. Drives the tank fill valve.",
};

export const actuatorHints: Record<ActuatorType, string> = {
  WATER_PUMP: "Waters a bed. Needs a soil moisture sensor.",
  FAN: "Cools or ventilates. Needs a temperature or humidity sensor.",
  FEEDER: "Refills a feed tray. Needs a feed level sensor.",
  HEATER: "Warms a house or a nursery. Needs a temperature sensor.",
  MISTER: "Sprays mist to raise the humidity. Needs a humidity sensor.",
  LIGHT: "Grow light or house light. Needs a light sensor, or run it on a schedule.",
  WATER_VALVE: "Fills a water tank. Needs a water tank level sensor.",
};

/** Suggested safety limit (minutes per run) when a technician picks the kind. */
export const defaultRunMinutes: Record<ActuatorType, number> = {
  WATER_PUMP: 10,
  FAN: 30,
  FEEDER: 5,
  HEATER: 30,
  MISTER: 5,
  LIGHT: 120,
  WATER_VALVE: 15,
};

export const roleLabels: Record<Role, string> = {
  OWNER: "Farm Owner",
  WORKER: "Farm Worker",
  TECHNICIAN: "Technician",
};
