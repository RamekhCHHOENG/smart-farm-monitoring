package com.smartfarm.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Abstract farm zone. New zone kinds (e.g. a fish pond) are added as subclasses
 * without changing the services that work with Zone (Open/Closed Principle).
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "zone_type")
public abstract class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Farm farm;

    // Composition: deleting a zone deletes its devices (Zone ◆── Sensor, Zone ◆── Actuator).
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Sensor> sensors = new ArrayList<>();

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Actuator> actuators = new ArrayList<>();

    protected Zone() {
    }

    protected Zone(Farm farm, String name) {
        this.farm = farm;
        this.name = name;
    }

    /** GRASP Creator: Zone contains sensors, so Zone creates them. */
    public Sensor addSensor(SensorType type, String name, boolean simulated) {
        Sensor sensor = Sensor.create(type, this, name, simulated);
        sensors.add(sensor);
        return sensor;
    }

    public Actuator addActuator(ActuatorType type, String name, Integer maxRunMinutes) {
        Actuator actuator = Actuator.create(type, this, name, maxRunMinutes);
        actuators.add(actuator);
        return actuator;
    }

    public void removeSensor(Sensor sensor) {
        sensors.remove(sensor);
    }

    public void removeActuator(Actuator actuator) {
        actuators.remove(actuator);
    }

    public Optional<Sensor> findSensor(SensorType type) {
        return sensors.stream().filter(s -> s.getType() == type).findFirst();
    }

    public Optional<Actuator> findActuator(ActuatorType type) {
        return actuators.stream()
                .filter(a -> a.getType() == type && a.getStatus() != ActuatorStatus.DECOMMISSIONED)
                .findFirst();
    }

    /** Running actuators in this zone that work against {@code actuator} (a fan while the heater starts). */
    public List<Actuator> runningOpposites(Actuator actuator) {
        return actuators.stream()
                .filter(a -> a != actuator && a.getStatus() == ActuatorStatus.RUNNING && actuator.opposes(a.getType()))
                .toList();
    }

    /** The kinds of actuator running in this zone right now; the sensors react to them in the simulator. */
    public Set<ActuatorType> runningActuatorTypes() {
        Set<ActuatorType> running = EnumSet.noneOf(ActuatorType.class);
        for (Actuator actuator : actuators) {
            if (actuator.getStatus() == ActuatorStatus.RUNNING) {
                running.add(actuator.getType());
            }
        }
        return running;
    }

    /**
     * Adds a rule for the first sensor of the given type, if the zone has one. It drives the first
     * working actuator of {@code actuatorType}; with no actuator (or {@code null}) it only raises an alert.
     * {@code stopAt} makes it keep a range (stop when the reading is back there); null = fixed run time.
     */
    protected void addRule(List<AutomationRule> rules, String name, SensorType sensorType, Condition condition,
                           double threshold, ActuatorType actuatorType, int durationMin, Severity severity, Double stopAt) {
        findSensor(sensorType).ifPresent(sensor -> {
            Actuator actuator = actuatorType == null ? null : findActuator(actuatorType).orElse(null);
            AutomationRule rule = new AutomationRule(name, sensor, condition, threshold, actuator,
                    actuator == null ? durationMin : Math.min(durationMin, actuator.getMaxRunMinutes()), severity);
            if (rule.getActuator() != null) {
                rule.holdUntil(stopAt);
            }
            rules.add(rule);
        });
    }

    /** Like {@link #addRule}, but only when the zone really has a working actuator of that type. */
    protected void addActuatorRule(List<AutomationRule> rules, String name, SensorType sensorType, Condition condition,
                                   double threshold, ActuatorType actuatorType, int durationMin, Severity severity, Double stopAt) {
        findActuator(actuatorType).ifPresent(actuator -> findSensor(sensorType).ifPresent(sensor -> {
            // never longer than the actuator's own safety limit, or it would refuse to start
            AutomationRule rule = new AutomationRule(name, sensor, condition, threshold, actuator,
                    Math.min(durationMin, actuator.getMaxRunMinutes()), severity);
            rule.holdUntil(stopAt);
            rules.add(rule);
        }));
    }

    /** Polymorphism: each zone kind proposes its own default automation rules. */
    public abstract List<AutomationRule> createDefaultRules();

    public abstract String getZoneType();

    public abstract String getDetail();

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Farm getFarm() { return farm; }
    public List<Sensor> getSensors() { return List.copyOf(sensors); }
    public List<Actuator> getActuators() { return List.copyOf(actuators); }
}
