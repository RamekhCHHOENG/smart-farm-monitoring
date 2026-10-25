package com.smartfarm.repository;

import com.smartfarm.domain.FarmDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FarmDeviceRepository extends JpaRepository<FarmDevice, Long> {

    boolean existsByModel_Id(Long modelId);

    long countByController_Id(Long controllerId);

    List<FarmDevice> findByController_IdOrderByIdAsc(Long controllerId);
}
