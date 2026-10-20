package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {

    List<DeviceLog> findByZoneIdAndAtBetweenOrderByAtDesc(Long zoneId, Instant from, Instant to);

    List<DeviceLog> findTop50ByActuatorIdOrderByAtDesc(Long actuatorId);
}
