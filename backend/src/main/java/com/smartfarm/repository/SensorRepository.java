package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    List<Sensor> findBySimulatedTrue();

    List<Sensor> findByOnlineTrue();
}
