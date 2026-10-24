package com.smartfarm.domain;

/** New kinds are added at the end; each one is a new Sensor subclass (Open/Closed Principle). */
public enum SensorType {
    SOIL_MOISTURE, TEMPERATURE, FEED_LEVEL, HUMIDITY, LIGHT_LEVEL, WATER_LEVEL
}
