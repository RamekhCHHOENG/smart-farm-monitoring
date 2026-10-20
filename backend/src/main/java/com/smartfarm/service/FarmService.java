package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Zones (UC-1, FR-01) and device registration (UC-2, FR-02). */
@Service
@Transactional
public class FarmService {

    private final FarmRepository farms;
    private final ZoneRepository zones;
    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final AutomationRuleRepository rules;
    private final AccessPolicy access;

    public FarmService(FarmRepository farms, ZoneRepository zones, SensorRepository sensors,
                       ActuatorRepository actuators, AutomationRuleRepository rules, AccessPolicy access) {
        this.farms = farms;
        this.zones = zones;
        this.sensors = sensors;
        this.actuators = actuators;
        this.rules = rules;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Farm farm() {
        return farms.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("No farm configured"));
    }

    public Zone createZone(Long userId, String type, String name, String cropName, Integer birdCount) {
        access.require(userId);
        Farm farm = farm();
        Zone zone = switch (type) {
            case "CROP" -> farm.addCropZone(name, cropName);
            case "POULTRY" -> farm.addPoultryZone(name, birdCount == null ? 0 : birdCount);
            default -> throw new IllegalArgumentException("Unknown zone type " + type);
        };
        farms.flush();
        return zone;
    }

    public Zone updateZone(Long userId, Long zoneId, String name, String cropName, Integer birdCount) {
        access.require(userId);
        Zone zone = zone(zoneId);
        zone.rename(name);
        if (zone instanceof CropZone crop && cropName != null) {
            crop.setCropName(cropName);
        } else if (zone instanceof PoultryZone poultry && birdCount != null) {
            poultry.setBirdCount(birdCount);
        }
        return zone;
    }

    /** Composition: removing the zone removes its sensors, actuators, readings, rules and tasks. */
    public void deleteZone(Long userId, Long zoneId) {
        access.require(userId);
        Zone zone = zone(zoneId);
        zone.getFarm().removeZone(zone);
    }

    public Sensor addSensor(Long userId, Long zoneId, SensorType type, String name, boolean simulated) {
        access.require(userId, Role.TECHNICIAN);
        Sensor sensor = zone(zoneId).addSensor(type, name, simulated);
        zones.flush();
        return sensor;
    }

    public Actuator addActuator(Long userId, Long zoneId, ActuatorType type, String name, Integer maxRunMinutes) {
        access.require(userId, Role.TECHNICIAN);
        Actuator actuator = zone(zoneId).addActuator(type, name, maxRunMinutes);
        zones.flush();
        return actuator;
    }

    public void removeSensor(Long userId, Long sensorId) {
        access.require(userId, Role.TECHNICIAN);
        Sensor sensor = sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId));
        sensor.getZone().removeSensor(sensor);
    }

    public void removeActuator(Long userId, Long actuatorId) {
        access.require(userId, Role.TECHNICIAN);
        Actuator actuator = actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId));
        actuator.getZone().removeActuator(actuator);
    }

    /** Polymorphism in action: each zone subclass proposes its own default rules. */
    public List<AutomationRule> applyDefaultRules(Long userId, Long zoneId) {
        access.require(userId);
        return rules.saveAll(zone(zoneId).createDefaultRules());
    }

    @Transactional(readOnly = true)
    public Zone zone(Long zoneId) {
        return zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone", zoneId));
    }
}
