package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

@Entity
@DiscriminatorValue("TEMPERATURE")
public class TemperatureSensor extends Sensor {

    protected TemperatureSensor() {
    }

    TemperatureSensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.TEMPERATURE; }
    @Override public String getUnit() { return "°C"; }
    @Override public double getMinValid() { return -10; }
    @Override public double getMaxValid() { return 60; }
    @Override public double initialSimulatedValue() { return 29; }

    private static final double DAY_AVERAGE = 31;

    /** Temperature wanders around a hot-day average, drops while a fan runs and rises while a heater runs. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double drift = (DAY_AVERAGE - previous) * 0.03 + (random.nextDouble() - 0.45) * 0.5;
        boolean cooling = running.contains(ActuatorType.FAN);
        boolean heating = running.contains(ActuatorType.HEATER);
        double delta = cooling || heating ? 0 : drift;
        if (cooling) {
            delta -= 0.05 + random.nextDouble() * 0.06;
        }
        if (heating) {
            delta += 0.3 + random.nextDouble() * 0.3;
        }
        return clamp(Math.max(24, Math.min(40, previous + delta)));
    }
}
