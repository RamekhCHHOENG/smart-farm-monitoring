package com.smartfarm.repository;

import com.smartfarm.domain.DeviceModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceModelRepository extends JpaRepository<DeviceModel, Long> {

    Optional<DeviceModel> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    List<DeviceModel> findAllByOrderByCategoryAscNameAsc();
}
