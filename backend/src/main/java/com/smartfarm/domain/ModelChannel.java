package com.smartfarm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One reading a sensor model gives: a DHT22 has two (temperature and humidity). */
@Embeddable
public class ModelChannel {

    @Column(name = "measurement_code", length = 40)
    private String measurementCode;

    private String label;

    protected ModelChannel() {
    }

    public ModelChannel(String measurementCode, String label) {
        this.measurementCode = measurementCode;
        this.label = label;
    }

    public String getMeasurementCode() { return measurementCode; }
    public String getLabel() { return label; }
}
