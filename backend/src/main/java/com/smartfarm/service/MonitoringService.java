package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.AutomationRuleRepository;
import com.smartfarm.repository.DeviceLogRepository;
import com.smartfarm.repository.SensorReadingRepository;
import com.smartfarm.repository.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates UC-3 "Submit Sensor Reading" (Deliverable 7). It only coordinates:
 * the sensor validates and creates the reading, each rule decides if it is
 * triggered, the actuator checks its own safety limit, and AlertService delivers.
 */
@Service
@Transactional
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final SensorRepository sensors;
    private final SensorReadingRepository readings;
    private final AutomationRuleRepository rules;
    private final DeviceLogRepository deviceLogs;
    private final AlertService alertService;
    private final ActuatorService actuatorService;
    private final Clock clock;

    public MonitoringService(SensorRepository sensors, SensorReadingRepository readings,
                             AutomationRuleRepository rules, DeviceLogRepository deviceLogs,
                             AlertService alertService, ActuatorService actuatorService, Clock clock) {
        this.sensors = sensors;
        this.readings = readings;
        this.rules = rules;
        this.deviceLogs = deviceLogs;
        this.alertService = alertService;
        this.actuatorService = actuatorService;
        this.clock = clock;
    }

    public ReadingResult processReading(Long sensorId, double value) {
        Instant now = clock.instant();
        Sensor sensor = sensors.findById(sensorId).orElseThrow(() -> new NotFoundException("Sensor", sensorId));

        // A sensor a technician reported as broken is not trusted: nothing is stored and no rule runs.
        if (sensor.getStatus() == SensorStatus.FAULT) {
            return ReadingResult.ignored(sensorId, sensor.getName() + " is marked faulty, so its readings are ignored until it is repaired");
        }

        // [valid?] no -> log sensor fault, mark sensor as suspect (Deliverable 8)
        if (!sensor.isValid(value)) {
            sensor.markSuspect(now);
            log.warn("Invalid reading {} from sensor {} ({})", value, sensorId, sensor.getName());
            return ReadingResult.invalid(sensorId, "Value " + value + " is outside the valid range "
                    + sensor.getMinValid() + "-" + sensor.getMaxValid() + " " + sensor.getUnit());
        }

        SensorReading reading = readings.save(sensor.record(value, now));

        // Under repair: the reading is kept so the technician can test the sensor, but no pump or fan reacts to it.
        if (sensor.isOutOfService()) {
            return ReadingResult.ok(reading.getId(), sensorId);
        }

        List<String> actions = new ArrayList<>();
        for (AutomationRule rule : rules.findBySensor_IdAndActiveTrue(sensorId)) {
            if (rule.isTargetReached(reading)) {
                // "keep in range": the reading is back to the stop value, so the run this rule started ends now
                if (actuatorService.stopAtTarget(rule, reading, now)) {
                    actions.add(rule.getActuator().getName() + " stopped: target reached");
                }
            } else if (rule.isTriggered(reading, now)) {
                actions.add(respond(rule, reading, now));
            }
        }
        return actions.isEmpty()
                ? ReadingResult.ok(reading.getId(), sensorId)
                : ReadingResult.actionTaken(reading.getId(), sensorId, actions);
    }

    /** alt [triggered]: activate the rule's actuator (if any) and raise an alert. */
    private String respond(AutomationRule rule, SensorReading reading, Instant now) {
        rule.markTriggered(now);
        Actuator actuator = rule.getActuator();
        Severity severity = rule.getSeverity();
        String outcome;

        List<String> interlocked = actuator != null && actuator.getStatus() == ActuatorStatus.IDLE
                ? actuatorService.stopOpposites(actuator, now) : List.of();
        if (actuator == null) {
            outcome = "alert only";
        } else if (actuator.activateForRule(rule.getDurationMin(), now, rule.getId())) {
            Sensor sensor = rule.getSensor();
            outcome = actuator.getActionVerb() + ": " + actuator.getName() + " started "
                    + (rule.keepsRange()
                        ? "until " + AutomationRule.format(rule.getStopAt()) + sensor.getUnit() + " (max " + rule.getDurationMin() + " min)"
                        : "for " + rule.getDurationMin() + " min");
            if (!interlocked.isEmpty()) {
                outcome += "; " + String.join(", ", interlocked);
            }
            deviceLogs.save(new DeviceLog(actuator, "ACTIVATED", LogSource.RULE, null, rule.getName(), now));
        } else if (actuator.getStatus() == ActuatorStatus.RUNNING) {
            outcome = actuator.getName() + " is already running";
        } else {
            // The actuator refused (fault, offline, maintenance or safety limit): a person must act.
            outcome = actuator.getName() + " could NOT start (status " + actuator.getStatus()
                    + ", max " + actuator.getMaxRunMinutes() + " min)";
            severity = Severity.CRITICAL;
        }

        alertService.raise(rule.describeBreach(reading) + " - " + outcome, severity,
                rule.getSensor().getZone().getId(), rule.getId());
        return outcome;
    }

    public record ReadingResult(String status, Long readingId, Long sensorId, List<String> actions, String error) {

        static ReadingResult ok(Long readingId, Long sensorId) {
            return new ReadingResult("OK", readingId, sensorId, List.of(), null);
        }

        static ReadingResult actionTaken(Long readingId, Long sensorId, List<String> actions) {
            return new ReadingResult("ACTION_TAKEN", readingId, sensorId, actions, null);
        }

        static ReadingResult invalid(Long sensorId, String error) {
            return new ReadingResult("INVALID", null, sensorId, List.of(), error);
        }

        /** Received, but deliberately not used; {@code error} says why. */
        static ReadingResult ignored(Long sensorId, String reason) {
            return new ReadingResult("IGNORED", null, sensorId, List.of(), reason);
        }

        public boolean isIgnored() {
            return "IGNORED".equals(status);
        }

        public boolean hasInvalidValue() {
            return "INVALID".equals(status);
        }
    }
}
