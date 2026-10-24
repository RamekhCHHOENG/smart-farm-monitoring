package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;
import java.util.Set;

/** How full a water tank is, in percent. */
@Entity
@DiscriminatorValue("WATER_LEVEL")
public class WaterLevelSensor extends Sensor {

    protected WaterLevelSensor() {
    }

    WaterLevelSensor(Zone zone, String name, boolean simulated) {
        super(zone, name, simulated);
    }

    @Override public SensorType getType() { return SensorType.WATER_LEVEL; }
    @Override public String getUnit() { return "%"; }
    @Override public double getMinValid() { return 0; }
    @Override public double getMaxValid() { return 100; }
    @Override public double initialSimulatedValue() { return 70; }

    /** The tank empties slowly, faster while a pump draws from it, and fills while the valve is open. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double delta = -(0.1 + random.nextDouble() * 0.2);
        if (running.contains(ActuatorType.WATER_PUMP)) {
            delta -= 0.5;
        }
        if (running.contains(ActuatorType.WATER_VALVE)) {
            delta += 2.5 + random.nextDouble();
        }
        return clamp(previous + delta);
    }
}
