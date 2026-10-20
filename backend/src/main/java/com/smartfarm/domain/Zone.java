package com.smartfarm.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** True when an actuator that corrects this sensor's reading is currently running. */
    public boolean isCorrecting(Sensor sensor) {
        return actuators.stream().anyMatch(a ->
                a.getType() == sensor.getCorrectiveActuatorType() && a.getStatus() == ActuatorStatus.RUNNING);
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
