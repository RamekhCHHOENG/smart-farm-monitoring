package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.DeviceLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Manual control (UC-6) and the technician's lifecycle actions (Deliverable 9). */
@Service
@Transactional
public class ActuatorService {

    public enum LifecycleAction { FAULT, OFFLINE, RESTORE, START_REPAIR, COMPLETE_REPAIR, DECOMMISSION }

    private final ActuatorRepository actuators;
    private final DeviceLogRepository deviceLogs;
    private final AlertService alertService;
    private final AccessPolicy access;
    private final Clock clock;

    public ActuatorService(ActuatorRepository actuators, DeviceLogRepository deviceLogs,
                           AlertService alertService, AccessPolicy access, Clock clock) {
        this.actuators = actuators;
        this.deviceLogs = deviceLogs;
        this.alertService = alertService;
        this.access = access;
        this.clock = clock;
    }

    public Actuator turnOn(Long userId, Long actuatorId, int durationMin) {
        AppUser user = access.require(userId, Role.WORKER);
        Actuator actuator = get(actuatorId);
        Instant now = clock.instant();
        if (actuator.getStatus() == ActuatorStatus.IDLE) {
            stopOpposites(actuator, now);
        }
        if (!actuator.activate(durationMin, now)) {
            throw new IllegalStateException("Cannot start " + actuator.getName() + ": it is " + actuator.getStatus()
                    + " and runs at most " + actuator.getMaxRunMinutes() + " min");
        }
        // UC-6 <<include>> Log Device Action
        deviceLogs.save(new DeviceLog(actuator, "ACTIVATED", LogSource.MANUAL, user.getName(), durationMin + " min", now));
        return actuator;
    }

    public Actuator turnOff(Long userId, Long actuatorId) {
        AppUser user = access.require(userId, Role.WORKER);
        Actuator actuator = get(actuatorId);
        if (!actuator.deactivate()) {
            throw new IllegalStateException(actuator.getName() + " is not running (" + actuator.getStatus() + ")");
        }
        deviceLogs.save(new DeviceLog(actuator, "DEACTIVATED", LogSource.MANUAL, user.getName(), null, clock.instant()));
        return actuator;
    }

    public Actuator lifecycle(Long userId, Long actuatorId, LifecycleAction action, boolean testPassed) {
        AppUser user = access.require(userId, Role.TECHNICIAN);
        Actuator actuator = get(actuatorId);
        Instant now = clock.instant();
        String detail = null;
        switch (action) {
            case FAULT -> {
                actuator.reportFault();
                alertService.raise(actuator.getName() + " reported a fault", Severity.CRITICAL, actuator.getZone().getId(), null);
            }
            case OFFLINE -> {
                actuator.heartbeatLost();
                alertService.raise(actuator.getName() + " went offline - technician needed", Severity.WARNING, actuator.getZone().getId(), null);
            }
            case RESTORE -> actuator.heartbeatRestored();
            case START_REPAIR -> actuator.startRepair();
            case COMPLETE_REPAIR -> detail = actuator.completeRepair(testPassed) ? "test passed" : "test failed - still in maintenance";
            case DECOMMISSION -> actuator.decommission();
        }
        deviceLogs.save(new DeviceLog(actuator, action.name(), LogSource.TECHNICIAN, user.getName(), detail, now));
        return actuator;
    }

    /**
     * Interlock: before {@code actuator} starts, every running actuator in its zone that works against it
     * (a fan when the heater starts) is stopped and logged. They never run together, whoever starts them.
     */
    public List<String> stopOpposites(Actuator actuator, Instant now) {
        List<String> stopped = new java.util.ArrayList<>();
        for (Actuator other : actuator.getZone().runningOpposites(actuator)) {
            if (other.deactivate()) {
                deviceLogs.save(new DeviceLog(other, "DEACTIVATED", LogSource.SAFETY, null,
                        "interlock: stopped so " + actuator.getName() + " can run (never together)", now));
                stopped.add(other.getName() + " stopped (interlock)");
            }
        }
        return stopped;
    }

    /** A "keep in range" rule reached its target: stop the run it started. */
    public boolean stopAtTarget(AutomationRule rule, SensorReading reading, Instant now) {
        Actuator actuator = rule.getActuator();
        if (actuator == null || !actuator.isRunningFor(rule.getId()) || !actuator.deactivate()) {
            return false;
        }
        Sensor sensor = rule.getSensor();
        deviceLogs.save(new DeviceLog(actuator, "DEACTIVATED", LogSource.RULE, null, "%s: target reached, %s reads %.1f%s (stop at %s%s)"
                .formatted(rule.getName(), sensor.getName(), reading.getValue(), sensor.getUnit(),
                        AutomationRule.format(rule.getStopValue()), sensor.getUnit()), now));
        return true;
    }

    /** Automatic safety shut-off: RUNNING → IDLE after the requested duration. */
    public int stopExpiredRuns() {
        Instant now = clock.instant();
        int stopped = 0;
        for (Actuator actuator : actuators.findByStatus(ActuatorStatus.RUNNING)) {
            if (actuator.isRunExpired(now) && actuator.deactivate()) {
                deviceLogs.save(new DeviceLog(actuator, "DEACTIVATED", LogSource.SAFETY, null, "run time finished", now));
                stopped++;
            }
        }
        return stopped;
    }

    /** Called by the scheduler for a due ScheduledTask (FR-07). */
    public void runScheduled(ScheduledTask task) {
        Actuator actuator = task.getActuator();
        Instant now = clock.instant();
        if (actuator.getStatus() == ActuatorStatus.IDLE) {
            stopOpposites(actuator, now);
        }
        if (actuator.activate(task.getDurationMin(), now)) {
            deviceLogs.save(new DeviceLog(actuator, "ACTIVATED", LogSource.SCHEDULE, null, task.getName(), now));
        } else {
            alertService.raise("Scheduled task '" + task.getName() + "' could not start " + actuator.getName()
                    + " (" + actuator.getStatus() + ")", Severity.WARNING, actuator.getZone().getId(), null);
        }
    }

    @Transactional(readOnly = true)
    public List<DeviceLog> logs(Long actuatorId) {
        return deviceLogs.findTop50ByActuatorIdOrderByAtDesc(actuatorId);
    }

    @Transactional(readOnly = true)
    public Actuator get(Long actuatorId) {
        return actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId));
    }
}
