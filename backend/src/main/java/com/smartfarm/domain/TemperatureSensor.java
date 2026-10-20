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

    /** Temperature drifts upward during the day and drops while the fan runs. */
    @Override
    public double simulateNext(double previous, boolean correcting, Random random) {
        double delta = correcting ? -(0.6 + random.nextDouble() * 0.6) : 0.1 + (random.nextDouble() - 0.4) * 0.6;
        return clamp(Math.max(24, Math.min(40, previous + delta)));
    }
}
