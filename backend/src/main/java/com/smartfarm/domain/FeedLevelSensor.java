package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

@Entity
@DiscriminatorValue("FEED_LEVEL")
public class FeedLevelSensor extends Sensor {

    protected FeedLevelSensor() {
    }

    FeedLevelSensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.FEED_LEVEL; }
    @Override public String getUnit() { return "%"; }
    @Override public double getMinValid() { return 0; }
    @Override public double getMaxValid() { return 100; }
    @Override public double initialSimulatedValue() { return 60; }

    /** Birds eat steadily; the feeder refills the tray quickly. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double delta = running.contains(ActuatorType.FEEDER)
                ? 2 + random.nextDouble()
                : -(0.1 + random.nextDouble() * 0.3);
        return clamp(previous + delta);
    }
}
