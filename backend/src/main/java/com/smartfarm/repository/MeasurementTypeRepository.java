package com.smartfarm.repository;

import com.smartfarm.domain.MeasurementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeasurementTypeRepository extends JpaRepository<MeasurementType, Long> {

    Optional<MeasurementType> findByCode(String code);

    boolean existsByCode(String code);

    List<MeasurementType> findAllByOrderByNameAsc();
}
