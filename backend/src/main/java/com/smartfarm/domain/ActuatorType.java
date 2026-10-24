package com.smartfarm.domain;

/** New kinds are added at the end; each one is a new Actuator subclass (Open/Closed Principle). */
public enum ActuatorType {
    WATER_PUMP, FAN, FEEDER, HEATER, MISTER, LIGHT, WATER_VALVE
}
