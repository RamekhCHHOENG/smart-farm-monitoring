package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;

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
    @Override public ActuatorType getCorrectiveActuatorType() { return ActuatorType.FAN; }
    @Override public double initialSimulatedValue() { return 29; }

    private static final double DAY_AVERAGE = 31;

    /** Temperature wanders around a hot-day average and drops while the fan runs. */
    @Override
    public double simulateNext(double previous, boolean correcting, Random random) {
        double drift = (DAY_AVERAGE - previous) * 0.03 + (random.nextDouble() - 0.45) * 0.5;
        double delta = correcting ? -(0.05 + random.nextDouble() * 0.06) : drift;
        return clamp(Math.max(24, Math.min(40, previous + delta)));
    }
}
