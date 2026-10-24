package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;

/**
 * "If sensor reading is ABOVE/BELOW threshold, run actuator for N minutes and alert."
 * The actuator is optional: a rule without one only raises an alert.
 *
 * With a {@code stopAt} value the rule keeps a range, like a thermostat (hysteresis): the actuator
 * runs until the reading is back to {@code stopAt}, and {@code durationMin} is only the longest run.
 * Example: fan starts above 34 °C and stops at 30 °C, instead of blowing for a fixed 5 minutes.
 */
@Entity
public class AutomationRule {

    public static final int DEFAULT_COOLDOWN_MINUTES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Sensor sensor;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_condition")
    private Condition condition;

    private double threshold;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Actuator actuator;

    private int durationMin;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private boolean active = true;

    /** Prevents alert spam: a rule fires at most once per cooldown window. */
    private int cooldownMinutes = DEFAULT_COOLDOWN_MINUTES;

    private Instant lastTriggeredAt;

    /** Null = fixed run time. Nullable column, so an existing database gets it without touching its rows. */
    private Double stopAt;

    protected AutomationRule() {
    }

    public AutomationRule(String name, Sensor sensor, Condition condition, double threshold,
                          Actuator actuator, int durationMin, Severity severity) {
        this.name = name;
        this.sensor = sensor;
        this.condition = condition;
        this.threshold = threshold;
        this.actuator = actuator;
        this.durationMin = durationMin;
        this.severity = severity;
    }

    /**
     * GRASP Information Expert: the rule owns condition, threshold and cooldown,
     * so the rule decides whether a reading triggers it.
     */
    public boolean isTriggered(SensorReading reading, Instant now) {
        if (!active || !reading.getSensorId().equals(sensor.getId())) {
            return false;
        }
        boolean breached = switch (condition) {
            case ABOVE -> reading.getValue() > threshold;
            case BELOW -> reading.getValue() < threshold;
        };
        return breached && !isCoolingDown(now);
    }

    private boolean isCoolingDown(Instant now) {
        return lastTriggeredAt != null
                && now.isBefore(lastTriggeredAt.plus(Duration.ofMinutes(cooldownMinutes)));
    }

    /**
     * Turns the rule into "keep in range" (or back to a fixed run with {@code null}). The stop value must lie
     * on the comfortable side of the threshold, otherwise the actuator would stop at once or never.
     */
    public void holdUntil(Double stopAt) {
        if (stopAt != null) {
            if (actuator == null) {
                throw new IllegalArgumentException("'Stop at' needs an actuator: an alert-only rule has nothing to stop");
            }
            if (condition == Condition.ABOVE && stopAt >= threshold) {
                throw new IllegalArgumentException("'Stop at' must be below " + format(threshold) + sensor.getUnit()
                        + " for a rule that starts above it");
            }
            if (condition == Condition.BELOW && stopAt <= threshold) {
                throw new IllegalArgumentException("'Stop at' must be above " + format(threshold) + sensor.getUnit()
                        + " for a rule that starts below it");
            }
        }
        this.stopAt = stopAt;
    }

    public boolean keepsRange() {
        return stopAt != null && actuator != null;
    }

    /** The reading is back in the comfortable range, so the actuator this rule started can stop. */
    public boolean isTargetReached(SensorReading reading) {
        if (!keepsRange()) {
            return false;
        }
        return condition == Condition.ABOVE ? reading.getValue() <= stopAt : reading.getValue() >= stopAt;
    }

    /** Where this rule's actuator stops pushing the reading: the stop value, or the threshold for a timed run. */
    public double getStopValue() {
        return stopAt != null ? stopAt : threshold;
    }

    /** 30.0 -> "30", 29.5 -> "29.5": how values appear in messages. */
    public static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public void markTriggered(Instant now) {
        lastTriggeredAt = now;
    }

    public String describeBreach(SensorReading reading) {
        return "%s: %s reads %.1f%s (%s %.1f%s)".formatted(
                sensor.getZone().getName(), sensor.getName(), reading.getValue(), sensor.getUnit(),
                condition == Condition.ABOVE ? "above" : "below", threshold, sensor.getUnit());
    }

    public void update(String name, Condition condition, double threshold, Actuator actuator,
                       int durationMin, Severity severity, int cooldownMinutes) {
        this.name = name;
        this.condition = condition;
        this.threshold = threshold;
        this.actuator = actuator;
        this.durationMin = durationMin;
        this.severity = severity;
        this.cooldownMinutes = cooldownMinutes;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Sensor getSensor() { return sensor; }
    public Condition getCondition() { return condition; }
    public double getThreshold() { return threshold; }
    public Actuator getActuator() { return actuator; }
    public int getDurationMin() { return durationMin; }
    public Severity getSeverity() { return severity; }
    public boolean isActive() { return active; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public Double getStopAt() { return stopAt; }
}
