package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.DeviceLogRepository;
import com.smartfarm.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * The technician's repair flow for a sensor (the sensor counterpart of the actuator state machine):
 * report a fault, start the repair, and complete it once the sensor proves itself with a valid reading.
 */
@Service
@Transactional
public class SensorService {

    public enum SensorAction { FAULT, START_REPAIR, COMPLETE_REPAIR }

    private final SensorRepository sensors;
    private final DeviceLogRepository deviceLogs;
    private final AlertService alertService;
    private final AccessPolicy access;
    private final Clock clock;

    public SensorService(SensorRepository sensors, DeviceLogRepository deviceLogs, AlertService alertService,
                         AccessPolicy access, Clock clock) {
        this.sensors = sensors;
        this.deviceLogs = deviceLogs;
        this.alertService = alertService;
        this.access = access;
        this.clock = clock;
    }

    public Sensor lifecycle(Long userId, Long sensorId, SensorAction action) {
        AppUser user = access.require(userId, Role.TECHNICIAN);
        Sensor sensor = sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId));
        Instant now = clock.instant();
        String detail = null;
        switch (action) {
            case FAULT -> {
                sensor.reportFault(now);
                // The farmer must know that nothing reacts to this sensor until it is repaired.
                alertService.raise(sensor.getZone().getName() + ": " + sensor.getName()
                                + " is faulty - its readings are ignored and its rules are paused until it is repaired",
                        Severity.WARNING, sensor.getZone().getId(), null);
            }
            case START_REPAIR -> sensor.startRepair(now);
            case COMPLETE_REPAIR -> detail = sensor.completeRepair()
                    ? "test passed"
                    : "test failed - no valid reading since the repair began";
        }
        deviceLogs.save(new DeviceLog(sensor, action.name(), LogSource.TECHNICIAN, user.getName(), detail, now));
        return sensor;
    }
}
