package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(indexes = @Index(columnList = "sensor_id, recordedAt"))
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A reading always knows its sensor (fixes the midterm gap: SensorReading had no sensor reference).
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Sensor sensor;

    @Column(name = "reading_value")
    private double value;

    private Instant recordedAt;

    protected SensorReading() {
    }

    SensorReading(Sensor sensor, double value, Instant recordedAt) {
        this.sensor = sensor;
        this.value = value;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Sensor getSensor() { return sensor; }
    public Long getSensorId() { return sensor.getId(); }
    public double getValue() { return value; }
    public Instant getRecordedAt() { return recordedAt; }
}
