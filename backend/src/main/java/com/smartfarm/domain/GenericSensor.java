package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import java.util.Random;
import java.util.Set;

/**
 * A sensor for a measurement added as master data (CO2, pH, ammonia...): no new class is needed.
 * Its unit and range come from its MeasurementType; it reacts to every running actuator whose catalog
 * effects name its measurement (Open/Closed: new kinds of reading without code changes).
 */
@Entity
@DiscriminatorValue("GENERIC")
public class GenericSensor extends Sensor {

    @ManyToOne(fetch = FetchType.LAZY)
    private MeasurementType measurement;

    protected GenericSensor() {
    }

    GenericSensor(Zone zone, String name, boolean simulated, MeasurementType measurement) {
        super(zone, name, simulated);
        this.measurement = measurement;
    }

    @Override public SensorType getType() { return null; }
    @Override public String getMeasurementCode() { return measurement.getCode(); }
    @Override public String getMeasurementName() { return measurement.getName(); }
    @Override public String getUnit() { return measurement.getUnit(); }
    @Override public double getMinValid() { return measurement.getMinValid(); }
    @Override public double getMaxValid() { return measurement.getMaxValid(); }
    @Override public double initialSimulatedValue() { return measurement.getTypical(); }

    /** Drifts back to the typical value; each running actuator that affects this measurement pushes it. */
    @Override
    public double simulateNext(double previous, Set<ActuatorType> running, Random random) {
        double span = getMaxValid() - getMinValid();
        double delta = (measurement.getTypical() - previous) * 0.03 + (random.nextDouble() - 0.5) * span * 0.004
                + getZone().netEffect(getMeasurementCode()) * span * 0.01;
        return clamp(previous + delta);
    }

    public MeasurementType getMeasurement() { return measurement; }
}
