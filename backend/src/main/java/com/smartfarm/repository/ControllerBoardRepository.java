package com.smartfarm.repository;

import com.smartfarm.domain.ControllerBoard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ControllerBoardRepository extends JpaRepository<ControllerBoard, Long> {

    Optional<ControllerBoard> findByKeyHash(String keyHash);

    List<ControllerBoard> findAllByOrderByIdAsc();

    List<ControllerBoard> findByFarm_IdOrderByIdAsc(Long farmId);
    List<ControllerBoard> findByFarmIsNull();
}
