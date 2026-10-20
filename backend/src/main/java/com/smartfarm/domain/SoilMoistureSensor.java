package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.Random;

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
    @Override public ActuatorType getCorrectiveActuatorType() { return ActuatorType.WATER_PUMP; }
    @Override public double initialSimulatedValue() { return 45; }

    /** Soil dries slowly in the sun and gets wet quickly while the pump runs. */
    @Override
    public double simulateNext(double previous, boolean correcting, Random random) {
        double delta = correcting ? 4 + random.nextDouble() * 3 : -(0.5 + random.nextDouble());
        return clamp(previous + delta);
    }
}
