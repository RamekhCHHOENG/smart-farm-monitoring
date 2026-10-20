package com.smartfarm.service;

import com.smartfarm.domain.Actuator;
import com.smartfarm.domain.ScheduledTask;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.ScheduledTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** UC-5 Schedule Farm Task (FR-07). */
@Service
@Transactional
public class TaskService {

    private final ScheduledTaskRepository tasks;
    private final ActuatorRepository actuators;
    private final ActuatorService actuatorService;
    private final AccessPolicy access;
    private final Clock clock;

    public TaskService(ScheduledTaskRepository tasks, ActuatorRepository actuators,
                       ActuatorService actuatorService, AccessPolicy access, Clock clock) {
        this.tasks = tasks;
        this.actuators = actuators;
        this.actuatorService = actuatorService;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ScheduledTask> list() {
        return tasks.findAllByOrderByRunTimeAsc();
    }

    public ScheduledTask create(Long userId, String name, Long actuatorId, LocalTime runTime,
                                Set<DayOfWeek> days, int durationMin) {
        access.require(userId);
        Actuator actuator = actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId));
        if (durationMin <= 0 || durationMin > actuator.getMaxRunMinutes()) {
            throw new IllegalArgumentException("Duration must be 1-" + actuator.getMaxRunMinutes() + " min for " + actuator.getName());
        }
        return tasks.save(new ScheduledTask(name, actuator, runTime, days, durationMin));
    }

    public ScheduledTask setActive(Long userId, Long taskId, boolean active) {
        access.require(userId);
        ScheduledTask task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Task", taskId));
        task.setActive(active);
        return task;
    }

    public void delete(Long userId, Long taskId) {
        access.require(userId);
        tasks.deleteById(taskId);
    }

    /** Runs every task whose time has come today. */
    public int runDueTasks() {
        LocalDateTime now = LocalDateTime.now(clock);
        int started = 0;
        for (ScheduledTask task : tasks.findByActiveTrue()) {
            if (task.isDue(now)) {
                task.markRun(now.toLocalDate());
                actuatorService.runScheduled(task);
                started++;
            }
        }
        return started;
    }
}
