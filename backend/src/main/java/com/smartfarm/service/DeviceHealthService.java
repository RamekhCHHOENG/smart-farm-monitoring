package com.smartfarm.service;

import com.smartfarm.domain.Sensor;
import com.smartfarm.domain.Severity;
import com.smartfarm.repository.SensorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** FR-09 (second half): alert when a sensor stops sending readings. */
@Service
@Transactional
public class DeviceHealthService {

    private final SensorRepository sensors;
    private final AlertService alertService;
    private final Clock clock;
    private final Duration offlineAfter;

    public DeviceHealthService(SensorRepository sensors, AlertService alertService, Clock clock,
                               @Value("${farm.sensor-offline-after:PT2M}") Duration offlineAfter) {
        this.sensors = sensors;
        this.alertService = alertService;
        this.clock = clock;
        this.offlineAfter = offlineAfter;
    }

    public int detectOfflineSensors() {
        Instant cutoff = clock.instant().minus(offlineAfter);
        int count = 0;
        for (Sensor sensor : sensors.findByOnlineTrue()) {
            if (sensor.markOfflineIfSilentSince(cutoff)) {
                alertService.raise(sensor.getZone().getName() + ": " + sensor.getName() + " is offline (no reading for "
                        + offlineAfter.toMinutes() + " min)", Severity.WARNING, sensor.getZone().getId(), null);
                count++;
            }
        }
        return count;
    }
}
