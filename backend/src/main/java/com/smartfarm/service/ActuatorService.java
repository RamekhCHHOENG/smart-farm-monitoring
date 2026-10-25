package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.ActuatorRepository;
import com.smartfarm.repository.DeviceLogRepository;
import com.smartfarm.repository.SensorReadingRepository;
import com.smartfarm.repository.SensorRepository;
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
    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final AlertService alertService;
    private final AccessPolicy access;
    private final Clock clock;

    private final Tenancy tenancy;


    public ActuatorService(ActuatorRepository actuators, DeviceLogRepository deviceLogs, SensorRepository sensors,
                           SensorReadingRepository readings, AlertService alertService, AccessPolicy access, Clock clock, Tenancy tenancy) {

        this.tenancy = tenancy;
        this.actuators = actuators;
        this.deviceLogs = deviceLogs;
        this.sensors = sensors;
        this.readings = readings;
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
            if (!actuator.isRunExpired(now)) {
                continue;
            }
            String noEffect = noEffectMessage(actuator);
            if (actuator.deactivate()) {
                deviceLogs.save(new DeviceLog(actuator, "DEACTIVATED", LogSource.SAFETY, null, "run time finished", now));
                stopped++;
                if (noEffect != null) {
                    alertService.raise(noEffect, Severity.WARNING, actuator.getZone().getId(), null);
                }
            }
        }
        return stopped;
    }

    /**
     * A rule's run used its whole time: did the reading move the way it should? A pump that runs while the pipe
     * is clogged, or a heater with a burnt element, changes nothing, and staff must hear about it.
     * Null when the run worked, was not started by a rule, or there is no fresh reading to judge by.
     */
    private String noEffectMessage(Actuator actuator) {
        if (actuator.getWatchSensorId() == null || actuator.getWatchSince() == null) {
            return null;
        }
        Sensor sensor = sensors.findById(actuator.getWatchSensorId()).orElse(null);
        SensorReading latest = sensor == null ? null : readings.findFirstBySensor_IdOrderByRecordedAtDesc(sensor.getId()).orElse(null);
        // No reading since the run began (sensor offline): the offline alert covers it, no guess here.
        if (latest == null || !latest.getRecordedAt().isAfter(actuator.getWatchSince())
                || actuator.movedAsExpected(latest.getValue(), sensor.getMinimumChange())) {
            return null;
        }
        return "%s: %s ran its full time but %s did not %s (%s → %s%s). Check %s."
                .formatted(actuator.getZone().getName(), actuator.getName(), sensor.getName(),
                        actuator.getWatchDirection() > 0 ? "rise" : "fall",
                        AutomationRule.format(actuator.getWatchStartValue()), AutomationRule.format(latest.getValue()),
                        sensor.getUnit(), actuator.getNoEffectHint());
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
        get(actuatorId); // another farm's actuator is "not found"
        return deviceLogs.findTop50ByActuatorIdOrderByAtDesc(actuatorId);
    }

    @Transactional(readOnly = true)
    public Actuator get(Long actuatorId) {
        return tenancy.actuator(actuators.findById(actuatorId).orElseThrow(() -> new NotFoundException("Actuator", actuatorId)));
    }
}
