package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;

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
    @Override public ActuatorType getCorrectiveActuatorType() { return ActuatorType.FEEDER; }
    @Override public double initialSimulatedValue() { return 60; }

    /** Birds eat steadily; the feeder refills the tray quickly. */
    @Override
    public double simulateNext(double previous, boolean correcting, Random random) {
        double delta = correcting ? 12 + random.nextDouble() * 6 : -(0.8 + random.nextDouble());
        return clamp(previous + delta);
    }
}
