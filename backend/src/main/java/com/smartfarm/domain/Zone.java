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

    /** A Farm Lab test zone: its alerts never reach staff phones or the farm's alert list. Nullable column. */
    private Boolean lab;

    // Composition: deleting a zone deletes its devices (Zone ◆── Sensor, Zone ◆── Actuator).
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Sensor> sensors = new ArrayList<>();

    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Actuator> actuators = new ArrayList<>();

    // The physical units in this zone (each made from a catalog model); their channels are listed above.
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<FarmDevice> devices = new ArrayList<>();

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

    /**
     * GRASP Creator: installs a device made from a catalog model, with one channel per reading (a DHT22 gives
     * temperature and humidity) or one output for an actuator. {@code measurements} maps the model's measurement
     * codes to their master data, for readings that have no built-in class (CO2, pH...).
     */
    public FarmDevice addDevice(DeviceModel model, String name, boolean simulated, Integer maxRunMinutes,
                                java.util.Map<String, MeasurementType> measurements, java.time.Instant now) {
        if (model.isRetired()) {
            throw new IllegalArgumentException(model.getName() + " is retired from the catalog");
        }
        FarmDevice device = new FarmDevice(this, model, name, now);
        switch (model.getCategory()) {
            case "SENSOR" -> {
                if (model.getChannels().isEmpty()) {
                    throw new IllegalArgumentException(model.getName() + " has no readings in the catalog");
                }
                boolean several = model.getChannels().size() > 1;
                for (ModelChannel channel : model.getChannels()) {
                    String channelName = several ? name + " · " + channel.getLabel() : name;
                    SensorType builtIn = DeviceModel.builtInSensorType(channel.getMeasurementCode());
                    Sensor sensor;
                    if (builtIn != null) {
                        sensor = Sensor.create(builtIn, this, channelName, simulated);
                    } else {
                        MeasurementType m = measurements.get(channel.getMeasurementCode());
                        if (m == null) {
                            throw new IllegalArgumentException("Unknown measurement " + channel.getMeasurementCode());
                        }
                        sensor = new GenericSensor(this, channelName, simulated, m);
                    }
                    sensor.attachTo(device);
                    sensors.add(sensor);
                }
            }
            case "ACTUATOR" -> {
                Integer limit = maxRunMinutes != null ? maxRunMinutes : model.getDefaultMaxRunMinutes();
                Actuator actuator = model.getActuatorType() != null
                        ? Actuator.create(ActuatorType.valueOf(model.getActuatorType()), this, name, limit)
                        : new GenericActuator(this, name, limit != null ? limit : 10, model.getName(), model.getActionVerb());
                actuator.attachTo(device);
                actuators.add(actuator);
            }
            default -> throw new IllegalArgumentException("A controller board is added on the Controllers page, not in a zone");
        }
        devices.add(device);
        return device;
    }

    /** Puts a channel made before the catalog existed into a device of its own (one-time upgrade). */
    public FarmDevice adopt(Sensor sensor, DeviceModel model, java.time.Instant now) {
        FarmDevice device = new FarmDevice(this, model, sensor.getName(), now);
        sensor.attachTo(device);
        devices.add(device);
        return device;
    }

    public FarmDevice adopt(Actuator actuator, DeviceModel model, java.time.Instant now) {
        FarmDevice device = new FarmDevice(this, model, actuator.getName(), now);
        actuator.attachTo(device);
        devices.add(device);
        return device;
    }

    /** Composition: removing a device removes its readings and outputs (and with them their rules and logs). */
    public void removeDevice(FarmDevice device) {
        sensors.removeIf(s -> s.getDevice() == device);
        actuators.removeIf(a -> a.getDevice() == device);
        devices.remove(device);
    }

    /** The sum of the directions (+1/-1) that running actuators push this measurement in. */
    public int netEffect(String measurementCode) {
        int net = 0;
        for (Actuator actuator : actuators) {
            if (actuator.getStatus() == ActuatorStatus.RUNNING) {
                for (ModelEffect effect : actuator.getEffects()) {
                    if (effect.getMeasurementCode().equals(measurementCode)) {
                        net += effect.getDirection();
                    }
                }
            }
        }
        return net;
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
            if (actuator.getStatus() == ActuatorStatus.RUNNING && actuator.getType() != null) {
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

    public boolean isLab() {
        return Boolean.TRUE.equals(lab);
    }

    public void markLab(boolean lab) {
        this.lab = lab;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Farm getFarm() { return farm; }
    public List<Sensor> getSensors() { return List.copyOf(sensors); }
    public List<Actuator> getActuators() { return List.copyOf(actuators); }
    public List<FarmDevice> getDevices() { return List.copyOf(devices); }
}
