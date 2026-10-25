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
  /** the farm role; null for Smart Farm company staff */
  role: Role | null;
  /** ADMIN or SUPPORT for company staff, null for farm accounts */
  companyRole: "ADMIN" | "SUPPORT" | null;
  farmId: number | null;
}

/** The farm a company account is working on (company staff choose; farm accounts always have their own). */
export interface ActiveFarm {
  id: number;
  name: string;
}

/** A customer's farm as the Smart Farm company sees it (GET /api/company/farms). */
export interface CompanyFarm {
  id: number;
  name: string;
  location: string;
  status: "ACTIVE" | "SUSPENDED";
  supportAllowed: boolean;
  createdAt: string | null;
  ownerContact: string | null;
  owners: string[];
  zones: number;
  sensors: number;
  sensorsOnline: number;
  actuatorsRunning: number;
  openAlerts: number;
  lastReadingAt: string | null;
  support: CompanyStaff[];
}

export interface CompanyStaff {
  id: number;
  username: string;
  name: string;
  companyRole: "ADMIN" | "SUPPORT";
}

/** What the server returns after making a farm or staff account: the invite token, shown once. */
export interface CompanyInvite {
  farm: CompanyFarm | null;
  staff: CompanyStaff | null;
  username: string;
  inviteToken: string;
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
  /** measurement code: a built-in SensorType or a catalog code like CO2 */
  type: string;
  measurementName: string;
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
  /** the physical device it belongs to (a DHT22 has two sensors) */
  deviceId: number | null;
  /** an ordinary healthy value (the simulator's resting point) */
  typical: number;
}

export interface Effect {
  measurementCode: string;
  /** +1 raises the measurement while running, -1 lowers it */
  direction: number;
}

export interface Actuator {
  id: number;
  zoneId: number;
  name: string;
  /** a built-in ActuatorType, or GENERIC for a kind added in the catalog */
  type: string;
  kindName: string;
  status: ActuatorStatus;
  maxRunMinutes: number;
  runningUntil: string | null;
  /** set while a rule's run is going */
  startedByRuleId: number | null;
  deviceId: number | null;
  effects: Effect[];
}

/** A physical unit on the farm, made from a catalog model. */
export interface FarmDevice {
  id: number;
  zoneId: number;
  name: string;
  modelId: number;
  modelName: string;
  category: DeviceCategory;
  wokwiPart: string | null;
  artType: string | null;
  hasPicture: boolean;
  controllerId: number | null;
  controllerName: string | null;
  serialNumber: string | null;
  notes: string | null;
  installedAt: string | null;
  sensorIds: number[];
  actuatorIds: number[];
}

export type DeviceCategory = "SENSOR" | "ACTUATOR" | "CONTROLLER";

export interface MeasurementType {
  id: number;
  code: string;
  name: string;
  unit: string;
  minValid: number;
  maxValid: number;
  typical: number;
  builtIn: boolean;
}

export interface DeviceModel {
  id: number;
  name: string;
  manufacturer: string | null;
  category: DeviceCategory;
  description: string | null;
  interfaceType: string | null;
  voltage: string | null;
  priceUsd: number | null;
  datasheetUrl: string | null;
  wiringNotes: string | null;
  wokwiPart: string | null;
  artType: string | null;
  channels: { measurementCode: string; label: string }[];
  actuatorType: string | null;
  actionVerb: string | null;
  defaultMaxRunMinutes: number | null;
  effects: Effect[];
  hasPicture: boolean;
  builtIn: boolean;
  retired: boolean;
  inUse: boolean;
}

export interface ControllerBoard {
  id: number;
  name: string;
  modelId: number | null;
  modelName: string | null;
  wokwiPart: string | null;
  keyHint: string | null;
  notes: string | null;
  lastSeenAt: string | null;
  deviceCount: number;
}

export interface Zone {
  id: number;
  name: string;
  type: "CROP" | "POULTRY";
  detail: string;
  cropName: string | null;
  birdCount: number | null;
  /** a Farm Lab test zone: its alerts stay in the lab */
  lab: boolean;
  sensors: Sensor[];
  actuators: Actuator[];
  devices: FarmDevice[];
}

export interface Farm {
  /** ACTIVE or SUSPENDED (by the Smart Farm company) */
  status: "ACTIVE" | "SUSPENDED";
  /** the owner allows Smart Farm company support to open the farm */
  supportAllowed: boolean;
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
  lab: boolean;
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

/** What a sensor measures, for labels: the built-in name, or the catalog measurement's. */
export const sensorKind = (s: Pick<Sensor, "type" | "measurementName">) =>
  sensorLabels[s.type as SensorType] ?? s.measurementName ?? s.type;

/** What kind of actuator it is: the built-in name, or the catalog model's. */
export const actuatorKind = (a: Pick<Actuator, "type" | "kindName">) =>
  actuatorLabels[a.type as ActuatorType] ?? a.kindName ?? a.type;

export const roleLabels: Record<Role, string> = {
  OWNER: "Farm Owner",
  WORKER: "Farm Worker",
  TECHNICIAN: "Technician",
};
