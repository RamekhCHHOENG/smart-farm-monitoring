package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

/** Air humidity (a DHT22 reports it together with the temperature). */
@Entity
@DiscriminatorValue("HUMIDITY")
public class HumiditySensor extends Sensor {

    private static final double TYPICAL = 68;

    protected HumiditySensor() {
    }

    HumiditySensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.HUMIDITY; }
    @Override public String getUnit() { return "%"; }
    @Override public double getMinValid() { return 0; }
    @Override public double getMaxValid() { return 100; }
    @Override public double initialSimulatedValue() { return 65; }

    /** Humidity wanders around a typical value, rises while a mister runs and falls while a fan ventilates. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double delta = (TYPICAL - previous) * 0.03 + (random.nextDouble() - 0.5) * 0.6;
        if (running.contains(ActuatorType.MISTER)) {
            delta += 1 + random.nextDouble() * 0.8;
        }
        if (running.contains(ActuatorType.FAN)) {
            delta -= 0.4 + random.nextDouble() * 0.3;
        }
        return clamp(previous + delta);
    }
}
