package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findTop200ByOrderByRaisedAtDesc();

    List<Alert> findByStatusOrderByRaisedAtDesc(AlertStatus status);

    long countByStatus(AlertStatus status);

    Optional<Alert> findFirstByStatusAndMessage(AlertStatus status, String message);
}
