package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Optional<SensorReading> findFirstBySensor_IdOrderByRecordedAtDesc(Long sensorId);

    List<SensorReading> findBySensor_IdAndRecordedAtBetweenOrderByRecordedAtAsc(Long sensorId, Instant from, Instant to);

    long countBySensor_IdAndRecordedAtBetween(Long sensorId, Instant from, Instant to);

    /** Every step-th reading, picked inside the database so a month of data is not loaded into memory. */
    @Query(value = """
            SELECT id, sensor_id, reading_value, recorded_at FROM (
                SELECT r.id, r.sensor_id, r.reading_value, r.recorded_at,
                       ROW_NUMBER() OVER (ORDER BY r.recorded_at) AS rn
                FROM sensor_reading r
                WHERE r.sensor_id = :sensorId AND r.recorded_at BETWEEN :from AND :to) t
            WHERE MOD(t.rn - 1, :step) = 0
            ORDER BY t.recorded_at""", nativeQuery = true)
    List<SensorReading> sampled(@Param("sensorId") Long sensorId, @Param("from") Instant from,
                                @Param("to") Instant to, @Param("step") long step);

    @Modifying
    @Query("delete from SensorReading r where r.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
