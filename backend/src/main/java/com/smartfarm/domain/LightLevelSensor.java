package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

/** Light in lux (a BH1750 reads 0-65535 lux; full sun is about 100000). */
@Entity
@DiscriminatorValue("LIGHT_LEVEL")
public class LightLevelSensor extends Sensor {

    private static final double TYPICAL = 15_000;

    protected LightLevelSensor() {
    }

    LightLevelSensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.LIGHT_LEVEL; }
    @Override public String getUnit() { return "lux"; }
    @Override public double getMinValid() { return 0; }
    @Override public double getMaxValid() { return 100_000; }
    @Override public double initialSimulatedValue() { return 15_000; }

    /** Daylight wanders around a typical value and gets brighter while a light is on. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double delta = (TYPICAL - previous) * 0.05 + (random.nextDouble() - 0.5) * 600;
        if (running.contains(ActuatorType.LIGHT)) {
            delta += 1_500 + random.nextDouble() * 500;
        }
        return clamp(previous + delta);
    }
}
