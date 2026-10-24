package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

@Entity
@DiscriminatorValue("SOIL_MOISTURE")
public class SoilMoistureSensor extends Sensor {

    protected SoilMoistureSensor() {
    }

    SoilMoistureSensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.SOIL_MOISTURE; }
    @Override public String getUnit() { return "%"; }
    @Override public double getMinValid() { return 0; }
    @Override public double getMaxValid() { return 100; }
    @Override public double initialSimulatedValue() { return 45; }

    /** Soil dries slowly in the sun and gets wet quickly while the pump runs. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double delta = running.contains(ActuatorType.WATER_PUMP)
                ? 0.8 + random.nextDouble() * 0.6
                : -(0.15 + random.nextDouble() * 0.35);
        return clamp(previous + delta);
    }
}
