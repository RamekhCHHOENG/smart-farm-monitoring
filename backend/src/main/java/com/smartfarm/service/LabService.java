package com.smartfarm.service;

import com.smartfarm.domain.Actuator;
import com.smartfarm.domain.Role;
import com.smartfarm.domain.Sensor;
import com.smartfarm.domain.Zone;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Farm Lab drives its virtual devices with the signed-in technician's account instead of the device key,
 * so the key never sits in a browser tab. Only devices in a lab zone can be driven this way: a real sensor's
 * readings still need the device key, so nobody can fake a real farm reading from the lab.
 */
@Service
@Transactional(readOnly = true)
public class LabService {

    private final SensorRepository sensors;
    private final ActuatorRepository actuators;
    private final AccessPolicy access;

    private final Tenancy tenancy;


    public LabService(SensorRepository sensors, ActuatorRepository actuators, AccessPolicy access, Tenancy tenancy) {

        this.tenancy = tenancy;
        this.sensors = sensors;
        this.actuators = actuators;
        this.access = access;
    }

    public Sensor labSensor(Long userId, Long sensorId) {
        access.require(userId, Role.TECHNICIAN);
        Sensor sensor = tenancy.sensor(sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId)));
        requireLab(sensor.getZone(), sensor.getName());
        return sensor;
    }

    public Actuator labActuator(Long userId, Long actuatorId) {
        access.require(userId, Role.TECHNICIAN);
        Actuator actuator = tenancy.actuator(actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId)));
        requireLab(actuator.getZone(), actuator.getName());
        return actuator;
    }

    private static void requireLab(Zone zone, String device) {
        if (!zone.isLab()) {
            throw new ForbiddenException(device + " is a real farm device: only devices in a lab zone can be driven "
                    + "from the Farm Lab without the device key");
        }
    }
}
