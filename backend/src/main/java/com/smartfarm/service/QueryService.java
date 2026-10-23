package com.smartfarm.service;

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

    public QueryService(SensorReadingRepository readings, DeviceLogRepository deviceLogs) {
        this.readings = readings;
        this.deviceLogs = deviceLogs;
    }

    public Optional<SensorReading> latest(Sensor sensor) {
        return readings.findFirstBySensor_IdOrderByRecordedAtDesc(sensor.getId());
    }

    /** Readings in a range, thinned to at most MAX_POINTS so charts stay fast. */
    public List<SensorReading> history(Long sensorId, Instant from, Instant to) {
        long count = readings.countBySensor_IdAndRecordedAtBetween(sensorId, from, to);
        if (count <= MAX_POINTS) {
            return readings.findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, from, to);
        }
        long step = (count + MAX_POINTS - 1) / MAX_POINTS;
        return readings.sampled(sensorId, from, to, step);
    }

    /** Every reading in the range, for export; the range is limited by the retention period. */
    public List<SensorReading> allReadings(Long sensorId, Instant from, Instant to) {
        return readings.findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(sensorId, from, to);
    }

    public List<DeviceLog> zoneLogs(Long zoneId, Instant from, Instant to) {
        return deviceLogs.findByZoneIdAndAtBetweenOrderByAtDesc(zoneId, from, to);
    }
}
