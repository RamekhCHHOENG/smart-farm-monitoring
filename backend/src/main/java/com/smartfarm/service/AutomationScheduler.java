package com.smartfarm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background clock of the system: safety shut-off, scheduled tasks, and offline detection. */
@Component
public class AutomationScheduler {

    private final ActuatorService actuatorService;
    private final TaskService taskService;
    private final DeviceHealthService deviceHealth;

    public AutomationScheduler(ActuatorService actuatorService, TaskService taskService, DeviceHealthService deviceHealth) {
        this.actuatorService = actuatorService;
        this.taskService = taskService;
        this.deviceHealth = deviceHealth;
    }

    @Scheduled(fixedDelayString = "${farm.scheduler.interval-ms:10000}", initialDelay = 5000)
    public void tick() {
        actuatorService.stopExpiredRuns();
        taskService.runDueTasks();
        deviceHealth.detectOfflineSensors();
    }
}
