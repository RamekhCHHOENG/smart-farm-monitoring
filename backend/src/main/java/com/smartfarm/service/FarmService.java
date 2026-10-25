package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartfarm.config.CatalogSeeder;

import java.time.Clock;
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
    private final DeviceModelRepository models;
    private final AccessPolicy access;
    private final Tenancy tenancy;
    private final Clock clock;

    public FarmService(FarmRepository farms, ZoneRepository zones, SensorRepository sensors,
                       ActuatorRepository actuators, AutomationRuleRepository rules, DeviceModelRepository models,
                       AccessPolicy access, Tenancy tenancy, Clock clock) {
        this.tenancy = tenancy;
        this.farms = farms;
        this.zones = zones;
        this.sensors = sensors;
        this.actuators = actuators;
        this.rules = rules;
        this.models = models;
        this.access = access;
        this.clock = clock;
    }

    /** The farm this request works on (see Tenancy); the first farm for background jobs and older callers. */
    @Transactional(readOnly = true)
    public Farm farm() {
        if (tenancy.limited()) {
            Long id = tenancy.farmId();
            return farms.findById(id).orElseThrow(() -> new NotFoundException("Farm", id));
        }
        if (tenancy.current().isPresent()) {
            throw new ForbiddenException("Choose a farm first"); // company staff on company pages
        }
        return farms.findFirstByOrderByIdAsc().orElseThrow(() -> new IllegalStateException("No farm configured"));
    }

    /** The owner allows or stops company support staff from opening this farm. */
    public Farm setSupportAccess(Long userId, boolean allowed) {
        AppUser user = access.require(userId);
        if (user.isCompany()) {
            throw new ForbiddenException("Only the farm's own owner decides about support access");
        }
        Farm farm = farm();
        farm.allowSupport(allowed);
        return farm;
    }

    public Farm updateFarm(Long userId, String name, String location) {
        access.require(userId);
        Farm farm = farm();
        farm.update(name, location);
        return farm;
    }

    public Zone createZone(Long userId, String type, String name, String cropName, Integer birdCount, Boolean lab) {
        access.require(userId);
        Farm farm = farm();
        Zone zone = switch (type) {
            case "CROP" -> farm.addCropZone(name, cropName);
            case "POULTRY" -> farm.addPoultryZone(name, birdCount == null ? 0 : birdCount);
            default -> throw new IllegalArgumentException("Unknown zone type " + type);
        };
        zone.markLab(Boolean.TRUE.equals(lab));
        farms.flush();
        return zone;
    }

    public Zone updateZone(Long userId, Long zoneId, String name, String cropName, Integer birdCount, Boolean lab) {
        access.require(userId);
        Zone zone = zone(zoneId);
        zone.rename(name);
        if (lab != null) {
            zone.markLab(lab);
        }
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
        Zone zone = zone(zoneId);
        Sensor sensor = zone.addSensor(type, name, simulated);
        // Quick add: the device is made from the catalog's usual model for this kind
        models.findByName(CatalogSeeder.DEFAULT_SENSOR_MODEL.get(type)).ifPresent(m -> zone.adopt(sensor, m, clock.instant()));
        zones.flush();
        return sensor;
    }

    public Actuator addActuator(Long userId, Long zoneId, ActuatorType type, String name, Integer maxRunMinutes) {
        access.require(userId, Role.TECHNICIAN);
        Zone zone = zone(zoneId);
        Actuator actuator = zone.addActuator(type, name, maxRunMinutes);
        models.findByName(CatalogSeeder.DEFAULT_ACTUATOR_MODEL.get(type)).ifPresent(m -> zone.adopt(actuator, m, clock.instant()));
        zones.flush();
        return actuator;
    }

    public Sensor renameSensor(Long userId, Long sensorId, String name) {
        access.require(userId, Role.TECHNICIAN);
        Sensor sensor = tenancy.sensor(sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId)));
        sensor.rename(name.trim());
        return sensor;
    }

    public Actuator updateActuator(Long userId, Long actuatorId, String name, int maxRunMinutes) {
        access.require(userId, Role.TECHNICIAN);
        Actuator actuator = tenancy.actuator(actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId)));
        actuator.update(name.trim(), maxRunMinutes);
        return actuator;
    }

    public void removeSensor(Long userId, Long sensorId) {
        access.require(userId, Role.TECHNICIAN);
        Sensor sensor = tenancy.sensor(sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId)));
        sensor.getZone().removeSensor(sensor);
    }

    public void removeActuator(Long userId, Long actuatorId) {
        access.require(userId, Role.TECHNICIAN);
        Actuator actuator = tenancy.actuator(actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId)));
        actuator.getZone().removeActuator(actuator);
    }

    /** Polymorphism in action: each zone subclass proposes its own default rules. */
    public List<AutomationRule> applyDefaultRules(Long userId, Long zoneId) {
        access.require(userId);
        return rules.saveAll(zone(zoneId).createDefaultRules());
    }

    @Transactional(readOnly = true)
    public Zone zone(Long zoneId) {
        return tenancy.zone(zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone", zoneId)));
    }
}
