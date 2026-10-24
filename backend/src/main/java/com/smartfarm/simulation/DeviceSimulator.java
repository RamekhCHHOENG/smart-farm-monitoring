package com.smartfarm.simulation;

import com.smartfarm.domain.Sensor;
import com.smartfarm.repository.SensorRepository;
import com.smartfarm.service.MonitoringService;
import com.smartfarm.service.QueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

/**
 * Plays the role of the "Sensor Device" actor when no hardware is connected.
 * Values react to actuators: soil gets wetter while the pump runs, the house
 * cools while the fan runs, and so on, so the full automation loop can be demonstrated.
 * Readings go through the same MonitoringService as real devices.
 */
@Component
@ConditionalOnProperty(name = "farm.simulator.enabled", havingValue = "true")
public class DeviceSimulator {

    private final SensorRepository sensors;
    private final MonitoringService monitoringService;
    private final QueryService queries;
    private final Random random = new Random();

    public DeviceSimulator(SensorRepository sensors, MonitoringService monitoringService, QueryService queries) {
        this.sensors = sensors;
        this.monitoringService = monitoringService;
        this.queries = queries;
    }

    @Scheduled(fixedRateString = "${farm.simulator.interval-ms:5000}", initialDelay = 2000)
    @Transactional
    public void tick() {
        for (Sensor sensor : sensors.findBySimulatedTrue()) {
            double previous = queries.latest(sensor).map(r -> r.getValue()).orElse(sensor.initialSimulatedValue());
            double next = sensor.simulateNext(previous, sensor.getZone().runningActuatorTypes(), random);
            monitoringService.processReading(sensor.getId(), next);
        }
    }
}
