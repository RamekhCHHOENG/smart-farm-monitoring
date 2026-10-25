package com.smartfarm.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One physical unit on the farm, made from a catalog model and placed in a zone: "House sensor 1, a DHT22".
 * Its readings and outputs are the channels (Sensor / Actuator) that rules, schedules and the dashboard use;
 * a DHT22 has two sensor channels, a pump one actuator channel.
 */
@Entity
@Table(name = "farm_device")
public class FarmDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Zone zone;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private DeviceModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ControllerBoard controller;

    private String serialNumber;

    /** Calibration and setup notes: soil dry/wet raw values, tank height, mounting place... */
    @Column(length = 1000)
    private String notes;

    private Instant installedAt;

    @OneToMany(mappedBy = "device")
    @OrderBy("id")
    private List<Sensor> sensors = new ArrayList<>();

    @OneToMany(mappedBy = "device")
    @OrderBy("id")
    private List<Actuator> actuators = new ArrayList<>();

    protected FarmDevice() {
    }

    FarmDevice(Zone zone, DeviceModel model, String name, Instant installedAt) {
        this.zone = zone;
        this.model = model;
        this.name = name;
        this.installedAt = installedAt;
    }

    public void update(String name, String serialNumber, String notes, ControllerBoard controller) {
        this.name = name;
        this.serialNumber = serialNumber;
        this.notes = notes;
        this.controller = controller;
    }

    void attach(Sensor sensor) {
        sensors.add(sensor);
    }

    void attach(Actuator actuator) {
        actuators.add(actuator);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Zone getZone() { return zone; }
    public DeviceModel getModel() { return model; }
    public ControllerBoard getController() { return controller; }
    public String getSerialNumber() { return serialNumber; }
    public String getNotes() { return notes; }
    public Instant getInstalledAt() { return installedAt; }
    public List<Sensor> getSensors() { return List.copyOf(sensors); }
    public List<Actuator> getActuators() { return List.copyOf(actuators); }
}
