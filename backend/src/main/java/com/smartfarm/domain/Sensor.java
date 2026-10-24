package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Random;
import java.util.Set;

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

    // Set by a technician (never by the system). Both empty = in service. Nullable columns, so an
    // existing database gets them added without touching its rows.
    private Instant faultReportedAt;
    private Instant repairStartedAt;

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
            case HUMIDITY -> new HumiditySensor(zone, name, simulated);
            case LIGHT_LEVEL -> new LightLevelSensor(zone, name, simulated);
            case WATER_LEVEL -> new WaterLevelSensor(zone, name, simulated);
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
        // A sensor that a technician already knows about is not reported as a new "offline" problem.
        if (getStatus() == SensorStatus.ONLINE && lastSeenAt != null && lastSeenAt.isBefore(cutoff)) {
            online = false;
            return true;
        }
        return false;
    }

    /** ONLINE / OFFLINE follow the signal; FAULT / MAINTENANCE are set by a technician and win over it. */
    public SensorStatus getStatus() {
        if (repairStartedAt != null) {
            return SensorStatus.MAINTENANCE;
        }
        if (faultReportedAt != null) {
            return SensorStatus.FAULT;
        }
        return online ? SensorStatus.ONLINE : SensorStatus.OFFLINE;
    }

    /** A broken or half-repaired sensor must never start a pump or a fan. */
    public boolean isOutOfService() {
        return faultReportedAt != null || repairStartedAt != null;
    }

    /** ONLINE / OFFLINE → FAULT: the sensor is stuck, drifting or broken; its readings are ignored. */
    public void reportFault(Instant at) {
        require(Set.of(SensorStatus.ONLINE, SensorStatus.OFFLINE), "report a fault");
        faultReportedAt = at;
    }

    /** FAULT / OFFLINE → MAINTENANCE: readings are stored again so the technician can test, but no rule runs. */
    public void startRepair(Instant at) {
        require(Set.of(SensorStatus.FAULT, SensorStatus.OFFLINE), "start repair");
        repairStartedAt = at;
    }

    /**
     * MAINTENANCE → ONLINE when the test passes: a valid reading arrived after the repair began.
     * Otherwise the sensor stays in MAINTENANCE. The test is the sensor's own data, not a promise.
     */
    public boolean completeRepair() {
        require(Set.of(SensorStatus.MAINTENANCE), "complete repair");
        boolean testPassed = online && !suspect && lastSeenAt != null && !lastSeenAt.isBefore(repairStartedAt);
        if (testPassed) {
            faultReportedAt = null;
            repairStartedAt = null;
        }
        return testPassed;
    }

    private void require(Set<SensorStatus> allowed, String action) {
        if (!allowed.contains(getStatus())) {
            throw new IllegalStateException("Cannot " + action + " while " + name + " is " + getStatus());
        }
    }

    public abstract SensorType getType();

    public abstract String getUnit();

    public abstract double getMinValid();

    public abstract double getMaxValid();

    public abstract double initialSimulatedValue();

    /** Next simulated value; {@code running} holds the kinds of actuator running in the zone right now. */
    public abstract double simulateNext(double previous, Set<ActuatorType> running, Random random);

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
    public Instant getFaultReportedAt() { return faultReportedAt; }
    public Instant getRepairStartedAt() { return repairStartedAt; }
}
