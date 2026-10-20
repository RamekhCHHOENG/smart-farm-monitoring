package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByActiveTrue();

    List<ScheduledTask> findAllByOrderByRunTimeAsc();
}
