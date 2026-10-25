package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActuatorRepository extends JpaRepository<Actuator, Long> {

    List<Actuator> findByStatus(ActuatorStatus status);

    List<Actuator> findByDeviceIsNull();
}
