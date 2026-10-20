package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Optional<SensorReading> findFirstBySensor_IdOrderByRecordedAtDesc(Long sensorId);

    List<SensorReading> findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(Long sensorId, Instant from, Instant to);
}
