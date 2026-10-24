package com.smartfarm.web;

import com.smartfarm.domain.*;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Request and response bodies of the REST API. Entities never leave the server directly. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- responses ----------

    public record UserDto(Long id, String username, String name, Role role) {
        static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getName(), u.getRole());
        }
    }

    public record ReadingDto(Long id, Long sensorId, double value, Instant recordedAt) {
        static ReadingDto of(SensorReading r) {
            return new ReadingDto(r.getId(), r.getSensorId(), r.getValue(), r.getRecordedAt());
        }
    }

    /** {@code status} is what staff see; {@code repairStartedAt} is set while the sensor is in MAINTENANCE. */
    public record SensorDto(Long id, Long zoneId, String name, SensorType type, String unit, double minValid,
                            double maxValid, boolean simulated, boolean online, boolean suspect, SensorStatus status,
                            Instant repairStartedAt, Instant lastSeenAt, ReadingDto latest) {
        static SensorDto of(Sensor s, SensorReading latest) {
            return new SensorDto(s.getId(), s.getZone().getId(), s.getName(), s.getType(), s.getUnit(),
                    s.getMinValid(), s.getMaxValid(), s.isSimulated(), s.isOnline(), s.isSuspect(), s.getStatus(),
                    s.getRepairStartedAt(), s.getLastSeenAt(), latest == null ? null : ReadingDto.of(latest));
        }
    }

    /** {@code startedByRuleId} is set while a rule's run is going, so staff can see what started it. */
    public record ActuatorDto(Long id, Long zoneId, String name, ActuatorType type, ActuatorStatus status,
                              int maxRunMinutes, Instant runningUntil, Long startedByRuleId) {
        static ActuatorDto of(Actuator a) {
            return new ActuatorDto(a.getId(), a.getZone().getId(), a.getName(), a.getType(), a.getStatus(),
                    a.getMaxRunMinutes(), a.getRunningUntil(), a.getStartedByRuleId());
        }
    }

    public record ZoneDto(Long id, String name, String type, String detail, String cropName, Integer birdCount,
                          List<SensorDto> sensors, List<ActuatorDto> actuators) {
    }

    public record FarmDto(Long id, String name, String location, String timeZone, long openAlerts, List<ZoneDto> zones) {
    }

    public record RuleDto(Long id, String name, Long sensorId, String sensorName, Long zoneId, String zoneName,
                          Condition condition, double threshold, String unit, Long actuatorId, String actuatorName,
                          int durationMin, Severity severity, boolean active, int cooldownMinutes,
                          Instant lastTriggeredAt, Double stopAt) {
        static RuleDto of(AutomationRule r) {
            Sensor s = r.getSensor();
            Actuator a = r.getActuator();
            return new RuleDto(r.getId(), r.getName(), s.getId(), s.getName(), s.getZone().getId(), s.getZone().getName(),
                    r.getCondition(), r.getThreshold(), s.getUnit(), a == null ? null : a.getId(),
                    a == null ? null : a.getName(), r.getDurationMin(), r.getSeverity(), r.isActive(),
                    r.getCooldownMinutes(), r.getLastTriggeredAt(), r.getStopAt());
        }
    }

    public record TaskDto(Long id, String name, Long actuatorId, String actuatorName, Long zoneId, String zoneName,
                          LocalTime runTime, Set<DayOfWeek> repeatDays, int durationMin, boolean active,
                          LocalDate lastRunDate, LocalDateTime nextRun) {
        static TaskDto of(ScheduledTask t, LocalDateTime now) {
            Actuator a = t.getActuator();
            return new TaskDto(t.getId(), t.getName(), a.getId(), a.getName(), a.getZone().getId(), a.getZone().getName(),
                    t.getRunTime(), t.getRepeatDays(), t.getDurationMin(), t.isActive(), t.getLastRunDate(), t.nextRun(now));
        }
    }

    public record AlertDto(Long id, String message, Severity severity, AlertStatus status, Instant raisedAt,
                           int repeatCount, Instant lastRaisedAt,
                           Long zoneId, Long ruleId, boolean delivered, String acknowledgedBy, Instant acknowledgedAt) {
        static AlertDto of(Alert a) {
            return new AlertDto(a.getId(), a.getMessage(), a.getSeverity(), a.getStatus(), a.getRaisedAt(),
                    a.getRepeatCount(), a.getLastRaisedAt(),
                    a.getZoneId(), a.getRuleId(), a.isDelivered(), a.getAcknowledgedBy(), a.getAcknowledgedAt());
        }
    }

    public record DeviceLogDto(Long id, Long zoneId, Long actuatorId, String actuatorName, String action,
                               LogSource source, String userName, String detail, Instant at) {
        static DeviceLogDto of(DeviceLog l) {
            return new DeviceLogDto(l.getId(), l.getZoneId(), l.getActuatorId(), l.getActuatorName(), l.getAction(),
                    l.getSource(), l.getUserName(), l.getDetail(), l.getAt());
        }
    }

    /** What an ESP32 needs to drive its relay. */
    public record ActuatorStateDto(Long id, ActuatorStatus status, boolean relayOn, Instant runningUntil) {
        static ActuatorStateDto of(Actuator a) {
            return new ActuatorStateDto(a.getId(), a.getStatus(), a.getStatus() == ActuatorStatus.RUNNING, a.getRunningUntil());
        }
    }

    // ---------- requests ----------

    public record ReadingRequest(@NotNull Long sensorId, @NotNull Double value) {
    }

    public record FarmRequest(@NotBlank @Size(max = 80) String name, @Size(max = 120) String location) {
    }

    public record ZoneRequest(@NotBlank String type, @NotBlank @Size(max = 80) String name,
                              String cropName, @PositiveOrZero Integer birdCount) {
    }

    public record SensorRequest(@NotNull SensorType type, @NotBlank @Size(max = 80) String name, Boolean simulated) {
    }

    public record ActuatorRequest(@NotNull ActuatorType type, @NotBlank @Size(max = 80) String name,
                                  @Positive @Max(240) Integer maxRunMinutes) {
    }

    public record RenameRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record ActuatorUpdateRequest(@NotBlank @Size(max = 80) String name, @Positive @Max(240) int maxRunMinutes) {
    }

    public record UserRequest(@NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 80) String name,
                              @NotNull Role role, @NotBlank String password) {
    }

    public record UserUpdateRequest(@NotBlank @Size(max = 80) String name, @NotNull Role role) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    public record RuleRequest(@NotBlank @Size(max = 80) String name, @NotNull Long sensorId, @NotNull Condition condition,
                              @NotNull Double threshold, Long actuatorId, @PositiveOrZero int durationMin,
                              @NotNull Severity severity, @Positive Integer cooldownMinutes, Double stopAt) {
    }

    public record TaskRequest(@NotBlank @Size(max = 80) String name, @NotNull Long actuatorId, @NotNull LocalTime runTime,
                              @NotEmpty Set<DayOfWeek> repeatDays, @Positive int durationMin) {
    }

    public record ActivateRequest(@Positive int durationMin) {
    }

    public record ActiveRequest(boolean active) {
    }

    /** Boolean, not boolean: actions other than COMPLETE_REPAIR send an empty body {}. */
    public record RepairRequest(Boolean testPassed) {
    }
}
