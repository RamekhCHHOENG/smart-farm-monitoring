package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Random;

/**
 * Abstract sensor. Subclasses define unit, valid range, which actuator corrects
 * their reading, and how the simulator moves their value (polymorphism).
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "sensor_type")
public abstract class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Zone zone;

    /** Simulated sensors get readings from the built-in simulator; real ones from an ESP32. */
    private boolean simulated;

    private boolean online = true;
    private boolean suspect;
    private Instant lastSeenAt;

    protected Sensor() {
    }

    protected Sensor(Zone zone, String name, boolean simulated) {
        this.zone = zone;
        this.name = name;
        this.simulated = simulated;
    }

    public void rename(String name) {
        this.name = name;
    }

    static Sensor create(SensorType type, Zone zone, String name, boolean simulated) {
        return switch (type) {
            case SOIL_MOISTURE -> new SoilMoistureSensor(zone, name, simulated);
            case TEMPERATURE -> new TemperatureSensor(zone, name, simulated);
            case FEED_LEVEL -> new FeedLevelSensor(zone, name, simulated);
        };
    }

    /** Information Expert: the sensor knows its own physical range. */
    public boolean isValid(double value) {
        return !Double.isNaN(value) && value >= getMinValid() && value <= getMaxValid();
    }

    /** GRASP Creator: a sensor produces readings, so it creates SensorReading. */
    public SensorReading record(double value, Instant at) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "Value " + value + " is outside the valid range of " + name + " (" + getMinValid() + "-" + getMaxValid() + ")");
        }
        lastSeenAt = at;
        online = true;
        suspect = false;
        return new SensorReading(this, value, at);
    }

    public void markSuspect(Instant at) {
        suspect = true;
        lastSeenAt = at;
    }

    /** @return true when the sensor just changed from online to offline */
    public boolean markOfflineIfSilentSince(Instant cutoff) {
        if (online && lastSeenAt != null && lastSeenAt.isBefore(cutoff)) {
            online = false;
            return true;
        }
        return false;
    }

    public abstract SensorType getType();

    public abstract String getUnit();

    public abstract double getMinValid();

    public abstract double getMaxValid();

    public abstract ActuatorType getCorrectiveActuatorType();

    public abstract double initialSimulatedValue();

    /** Next simulated value; {@code correcting} is true while the corrective actuator runs. */
    public abstract double simulateNext(double previous, boolean correcting, Random random);

    protected double clamp(double value) {
        return Math.round(Math.max(getMinValid(), Math.min(getMaxValid(), value)) * 10) / 10.0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Zone getZone() { return zone; }
    public boolean isSimulated() { return simulated; }
    public boolean isOnline() { return online; }
    public boolean isSuspect() { return suspect; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
