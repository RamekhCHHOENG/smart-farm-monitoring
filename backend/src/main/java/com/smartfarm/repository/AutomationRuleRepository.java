package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    List<AutomationRule> findBySensor_IdAndActiveTrue(Long sensorId);

    List<AutomationRule> findBySensor_Id(Long sensorId);

    List<AutomationRule> findAllByOrderByIdAsc();

    List<AutomationRule> findBySensor_Zone_Farm_IdOrderByIdAsc(Long farmId);
}
