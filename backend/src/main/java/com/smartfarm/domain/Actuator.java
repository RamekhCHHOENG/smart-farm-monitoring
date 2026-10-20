package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Abstract actuator. Its status only changes through the transitions of the
 * state machine (Deliverable 9); an invalid transition throws IllegalStateException.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "actuator_type")
public abstract class Actuator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    private ActuatorStatus status = ActuatorStatus.IDLE;

    private int maxRunMinutes;
    private Instant runningUntil;

    protected Actuator() {
    }

    protected Actuator(Zone zone, String name, int maxRunMinutes) {
        if (maxRunMinutes <= 0) {
            throw new IllegalArgumentException("maxRunMinutes must be positive");
        }
        this.zone = zone;
        this.name = name;
        this.maxRunMinutes = maxRunMinutes;
    }

    static Actuator create(ActuatorType type, Zone zone, String name, Integer maxRunMinutes) {
        return switch (type) {
            case WATER_PUMP -> new WaterPump(zone, name, maxRunMinutes != null ? maxRunMinutes : WaterPump.DEFAULT_MAX_MINUTES);
            case FAN -> new Fan(zone, name, maxRunMinutes != null ? maxRunMinutes : Fan.DEFAULT_MAX_MINUTES);
            case FEEDER -> new Feeder(zone, name, maxRunMinutes != null ? maxRunMinutes : Feeder.DEFAULT_MAX_MINUTES);
        };
    }

    /** IDLE → RUNNING when [safetyOk]. Returns false (no state change) when the guard fails. */
    public boolean activate(int durationMin, Instant now) {
        if (status != ActuatorStatus.IDLE || !checkSafetyLimit(durationMin)) {
            return false;
        }
        status = ActuatorStatus.RUNNING;
        runningUntil = now.plus(Duration.ofMinutes(durationMin));
        return true;
    }

    /** RUNNING → IDLE. Returns false when the actuator was not running. */
    public boolean deactivate() {
        if (status != ActuatorStatus.RUNNING) {
            return false;
        }
        status = ActuatorStatus.IDLE;
        runningUntil = null;
        return true;
    }

    /** Safety shut-off: RUNNING → IDLE after the requested duration. */
    public boolean isRunExpired(Instant now) {
        return status == ActuatorStatus.RUNNING && runningUntil != null && !now.isBefore(runningUntil);
    }

    /** IDLE/RUNNING → FAULT */
    public void reportFault() {
        require(Set.of(ActuatorStatus.IDLE, ActuatorStatus.RUNNING), "report a fault");
        status = ActuatorStatus.FAULT;
        runningUntil = null;
    }

    /** IDLE/RUNNING → OFFLINE */
    public void heartbeatLost() {
        require(Set.of(ActuatorStatus.IDLE, ActuatorStatus.RUNNING), "mark offline");
        status = ActuatorStatus.OFFLINE;
        runningUntil = null;
    }

    /** OFFLINE → IDLE */
    public void heartbeatRestored() {
        require(Set.of(ActuatorStatus.OFFLINE), "restore heartbeat");
        status = ActuatorStatus.IDLE;
    }

    /** FAULT/OFFLINE → MAINTENANCE */
    public void startRepair() {
        require(Set.of(ActuatorStatus.FAULT, ActuatorStatus.OFFLINE), "start repair");
        status = ActuatorStatus.MAINTENANCE;
    }

    /** MAINTENANCE → IDLE when [testPassed]; otherwise it stays in MAINTENANCE. */
    public boolean completeRepair(boolean testPassed) {
        require(Set.of(ActuatorStatus.MAINTENANCE), "complete repair");
        if (testPassed) {
            status = ActuatorStatus.IDLE;
        }
        return testPassed;
    }

    /** MAINTENANCE → DECOMMISSIONED (final state). */
    public void decommission() {
        require(Set.of(ActuatorStatus.MAINTENANCE), "decommission");
        status = ActuatorStatus.DECOMMISSIONED;
    }

    private boolean checkSafetyLimit(int durationMin) {
        return durationMin > 0 && durationMin <= maxRunMinutes;
    }

    private void require(Set<ActuatorStatus> allowed, String action) {
        if (!allowed.contains(status)) {
            throw new IllegalStateException("Cannot " + action + " while " + name + " is " + status);
        }
    }

    public abstract ActuatorType getType();

    /** Short verb used in alerts and logs, e.g. "Watering". */
    public abstract String getActionVerb();

    public Long getId() { return id; }
    public String getName() { return name; }
    public Zone getZone() { return zone; }
    public ActuatorStatus getStatus() { return status; }
    public int getMaxRunMinutes() { return maxRunMinutes; }
    public Instant getRunningUntil() { return runningUntil; }
}
