package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Duration;
import java.time.Instant;

/**
 * "If sensor reading is ABOVE/BELOW threshold, run actuator for N minutes and alert."
 * The actuator is optional: a rule without one only raises an alert.
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
}
