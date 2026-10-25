package com.smartfarm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * What an actuator model does to a measurement while it runs: +1 raises it (a heater, temperature),
 * -1 lowers it (a fan, temperature). Used by the simulator and the Farm Lab, and to explain a device.
 */
@Embeddable
public class ModelEffect {

    @Column(name = "measurement_code", length = 40)
    private String measurementCode;

    private int direction;

    protected ModelEffect() {
    }

    public ModelEffect(String measurementCode, int direction) {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("An effect raises (+1) or lowers (-1) a measurement");
        }
        this.measurementCode = measurementCode;
        this.direction = direction;
    }

    public String getMeasurementCode() { return measurementCode; }
    public int getDirection() { return direction; }
}
