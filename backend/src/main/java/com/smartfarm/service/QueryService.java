package com.smartfarm.service;

import com.smartfarm.repository.SensorRepository;
import com.smartfarm.repository.ZoneRepository;

import com.smartfarm.domain.DeviceLog;
import com.smartfarm.domain.Sensor;
import com.smartfarm.domain.SensorReading;
import com.smartfarm.repository.DeviceLogRepository;
import com.smartfarm.repository.SensorReadingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-only queries for the dashboard (FR-04) and history reports (FR-10). */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private static final int MAX_POINTS = 500;

    private final SensorReadingRepository readings;
    private final DeviceLogRepository deviceLogs;
    private final SensorRepository sensors;
    private final ZoneRepository zones;
    private final Tenancy tenancy;

    public QueryService(SensorReadingRepository readings, DeviceLogRepository deviceLogs, SensorRepository sensors,
                        ZoneRepository zones, Tenancy tenancy) {
        this.readings = readings;
        this.deviceLogs = deviceLogs;
        this.sensors = sensors;
        this.zones = zones;
        this.tenancy = tenancy;
    }

    /** Another farm's sensor or zone is answered as "not found". */
    private void checkSensor(Long sensorId) {
        if (tenancy.limited()) {
            tenancy.sensor(sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId)));
        }
    }

    private void checkZone(Long zoneId) {
        if (tenancy.limited()) {
            tenancy.zone(zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone", zoneId)));
        }
    }

    public Optional<SensorReading> latest(Sensor sensor) {
        return readings.findFirstBySensor_IdOrderByRecordedAtDesc(sensor.getId());
    }

    /** Readings in a range, thinned to at most MAX_POINTS so charts stay fast. */
    public List<SensorReading> history(Long sensorId, Instant from, Instant to) {
        checkSensor(sensorId);
        long count = readings.countBySensor_IdAndRecordedAtBetween(sensorId, from, to);
        if (count <= MAX_POINTS) {
            return readings.findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, from, to);
        }
        long step = (count + MAX_POINTS - 1) / MAX_POINTS;
        return readings.sampled(sensorId, from, to, step);
    }

    /** Every reading in the range, for export; the range is limited by the retention period. */
    public List<SensorReading> allReadings(Long sensorId, Instant from, Instant to) {
        checkSensor(sensorId);
        return readings.findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, from, to);
    }

    public List<DeviceLog> zoneLogs(Long zoneId, Instant from, Instant to) {
        checkZone(zoneId);
        return deviceLogs.findByZoneIdAndAtBetweenOrderByAtDesc(zoneId, from, to);
    }
}
