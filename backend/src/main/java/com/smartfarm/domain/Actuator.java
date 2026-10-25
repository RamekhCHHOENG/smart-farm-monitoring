package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    /** The physical unit this output belongs to. Nullable: set by the catalog upgrade. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private FarmDevice device;

    @Enumerated(EnumType.STRING)
    private ActuatorStatus status = ActuatorStatus.IDLE;

    private int maxRunMinutes;
    private Instant runningUntil;

    /** The rule that started the current run, so only that rule stops it at its target. Nullable column. */
    private Long startedByRuleId;

    // What a rule's run should change, so the end of the run can check that it really worked
    // (a pump that runs while the pipe is clogged). All nullable: cleared whenever the run ends.
    private Long watchSensorId;
    private Double watchStartValue;
    private Integer watchDirection;
    private Instant watchSince;

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

    /** Name and safety limit can change; the status only changes through the state machine. */
    public void update(String name, int maxRunMinutes) {
        if (maxRunMinutes <= 0) {
            throw new IllegalArgumentException("maxRunMinutes must be positive");
        }
        this.name = name;
        this.maxRunMinutes = maxRunMinutes;
    }

    static Actuator create(ActuatorType type, Zone zone, String name, Integer maxRunMinutes) {
        return switch (type) {
            case WATER_PUMP -> new WaterPump(zone, name, maxRunMinutes != null ? maxRunMinutes : WaterPump.DEFAULT_MAX_MINUTES);
            case FAN -> new Fan(zone, name, maxRunMinutes != null ? maxRunMinutes : Fan.DEFAULT_MAX_MINUTES);
            case FEEDER -> new Feeder(zone, name, maxRunMinutes != null ? maxRunMinutes : Feeder.DEFAULT_MAX_MINUTES);
            case HEATER -> new Heater(zone, name, maxRunMinutes != null ? maxRunMinutes : Heater.DEFAULT_MAX_MINUTES);
            case MISTER -> new Mister(zone, name, maxRunMinutes != null ? maxRunMinutes : Mister.DEFAULT_MAX_MINUTES);
            case LIGHT -> new Light(zone, name, maxRunMinutes != null ? maxRunMinutes : Light.DEFAULT_MAX_MINUTES);
            case WATER_VALVE -> new WaterValve(zone, name, maxRunMinutes != null ? maxRunMinutes : WaterValve.DEFAULT_MAX_MINUTES);
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

    /** Like {@link #activate}, but remembers which rule started the run. */
    public boolean activateForRule(int durationMin, Instant now, Long ruleId) {
        if (!activate(durationMin, now)) {
            return false;
        }
        startedByRuleId = ruleId;
        return true;
    }

    /** RUNNING → IDLE. Returns false when the actuator was not running. */
    public boolean deactivate() {
        if (status != ActuatorStatus.RUNNING) {
            return false;
        }
        status = ActuatorStatus.IDLE;
        clearRun();
        return true;
    }

    /**
     * The run should move {@code sensor} from {@code startValue} up ({@code direction} +1) or down (-1).
     * Checked when the run's time is up: see {@link #movedAsExpected}.
     */
    public void expectEffect(Sensor sensor, double startValue, int direction, Instant now) {
        watchSensorId = sensor.getId();
        watchStartValue = startValue;
        watchDirection = direction;
        watchSince = now;
    }

    /** True when the reading moved at least {@code minChange} in the expected direction since the run began. */
    public boolean movedAsExpected(double latestValue, double minChange) {
        return watchStartValue == null || watchDirection == null
                || (latestValue - watchStartValue) * watchDirection >= minChange;
    }

    private void clearRun() {
        runningUntil = null;
        startedByRuleId = null;
        watchSensorId = null;
        watchStartValue = null;
        watchDirection = null;
        watchSince = null;
    }

    /** What to check when a run changed nothing; each kind of actuator knows its usual weak spot. */
    public String getNoEffectHint() {
        return "the device and its supply";
    }

    /** True while this run was started by the given rule (a person's or a schedule's run is never stopped by a rule). */
    public boolean isRunningFor(Long ruleId) {
        return status == ActuatorStatus.RUNNING && ruleId != null && ruleId.equals(startedByRuleId);
    }

    /**
     * Actuators that work against each other in one zone (a heater and a fan) must never run together.
     * Subclasses that have an opposite say so (Information Expert).
     */
    public boolean opposes(ActuatorType other) {
        return false;
    }

    /** Safety shut-off: RUNNING → IDLE after the requested duration. */
    public boolean isRunExpired(Instant now) {
        return status == ActuatorStatus.RUNNING && runningUntil != null && !now.isBefore(runningUntil);
    }

    /** IDLE/RUNNING → FAULT */
    public void reportFault() {
        require(Set.of(ActuatorStatus.IDLE, ActuatorStatus.RUNNING), "report a fault");
        status = ActuatorStatus.FAULT;
        clearRun();
    }

    /** IDLE/RUNNING → OFFLINE */
    public void heartbeatLost() {
        require(Set.of(ActuatorStatus.IDLE, ActuatorStatus.RUNNING), "mark offline");
        status = ActuatorStatus.OFFLINE;
        clearRun();
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

    void attachTo(FarmDevice device) {
        this.device = device;
        device.attach(this);
    }

    /** Built-in kind, or null for a kind added in the catalog (see GenericActuator). */
    public abstract ActuatorType getType();

    /** WATER_PUMP, FAN... or GENERIC for a kind added in the catalog. */
    public String getKindCode() {
        return getType().name();
    }

    /** What staff call it: a built-in kind's name, or the catalog model's. */
    public String getKindName() {
        return switch (getType()) {
            case WATER_PUMP -> "Water pump";
            case FAN -> "Fan";
            case FEEDER -> "Feeder";
            case HEATER -> "Heater";
            case MISTER -> "Mister";
            case LIGHT -> "Light";
            case WATER_VALVE -> "Tank fill valve";
        };
    }

    /** What running this actuator does to the zone's measurements (the simulator and the Farm Lab use it). */
    public List<ModelEffect> getEffects() {
        return List.of();
    }

    /** Short verb used in alerts and logs, e.g. "Watering". */
    public abstract String getActionVerb();

    public Long getId() { return id; }
    public String getName() { return name; }
    public Zone getZone() { return zone; }
    public ActuatorStatus getStatus() { return status; }
    public int getMaxRunMinutes() { return maxRunMinutes; }
    public Instant getRunningUntil() { return runningUntil; }
    public Long getStartedByRuleId() { return startedByRuleId; }
    public FarmDevice getDevice() { return device; }
    public Long getWatchSensorId() { return watchSensorId; }
    public Double getWatchStartValue() { return watchStartValue; }
    public Integer getWatchDirection() { return watchDirection; }
    public Instant getWatchSince() { return watchSince; }
}
