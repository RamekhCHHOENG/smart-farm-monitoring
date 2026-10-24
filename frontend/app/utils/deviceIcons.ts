// One icon per kind of device, used by the dashboard, the Zones page and the Devices pages.
import {
  CloudFog, Droplet, Droplets, Fan, Flame, GlassWater, Lightbulb, SprayCan, Sun, Thermometer, Utensils, Waves, Wheat,
} from "lucide-vue-next";
import type { ActuatorType, SensorType } from "./types";

export const sensorIcons: Record<SensorType, typeof Sun> = {
  SOIL_MOISTURE: Droplets, TEMPERATURE: Thermometer, FEED_LEVEL: Wheat,
  HUMIDITY: CloudFog, LIGHT_LEVEL: Sun, WATER_LEVEL: GlassWater,
};

export const actuatorIcons: Record<ActuatorType, typeof Sun> = {
  WATER_PUMP: Waves, FAN: Fan, FEEDER: Utensils, HEATER: Flame, MISTER: SprayCan, LIGHT: Lightbulb, WATER_VALVE: Droplet,
};
