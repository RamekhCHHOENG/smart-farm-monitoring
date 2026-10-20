package com.smartfarm.web;

import com.smartfarm.domain.*;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Request and response bodies of the REST API. Entities never leave the server directly. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- responses ----------

    public record UserDto(Long id, String name, Role role) {
        static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getName(), u.getRole());
        }
    }

    public record ReadingDto(Long id, Long sensorId, double value, Instant recordedAt) {
        static ReadingDto of(SensorReading r) {
            return new ReadingDto(r.getId(), r.getSensorId(), r.getValue(), r.getRecordedAt());
        }
    }

    public record SensorDto(Long id, Long zoneId, String name, SensorType type, String unit, double minValid,
                            double maxValid, boolean simulated, boolean online, boolean suspect,
                            Instant lastSeenAt, ReadingDto latest) {
        static SensorDto of(Sensor s, SensorReading latest) {
            return new SensorDto(s.getId(), s.getZone().getId(), s.getName(), s.getType(), s.getUnit(),
                    s.getMinValid(), s.getMaxValid(), s.isSimulated(), s.isOnline(), s.isSuspect(),
                    s.getLastSeenAt(), latest == null ? null : ReadingDto.of(latest));
        }
    }

    public record ActuatorDto(Long id, Long zoneId, String name, ActuatorType type, ActuatorStatus status,
                              int maxRunMinutes, Instant runningUntil) {
        static ActuatorDto of(Actuator a) {
            return new ActuatorDto(a.getId(), a.getZone().getId(), a.getName(), a.getType(), a.getStatus(),
                    a.getMaxRunMinutes(), a.getRunningUntil());
        }
    }

    public record ZoneDto(Long id, String name, String type, String detail, String cropName, Integer birdCount,
                          List<SensorDto> sensors, List<ActuatorDto> actuators) {
    }

    public record FarmDto(Long id, String name, String location, long openAlerts, List<ZoneDto> zones) {
    }

    public record RuleDto(Long id, String name, Long sensorId, String sensorName, Long zoneId, String zoneName,
                          Condition condition, double threshold, String unit, Long actuatorId, String actuatorName,
                          int durationMin, Severity severity, boolean active, int cooldownMinutes,
                          Instant lastTriggeredAt) {
        static RuleDto of(AutomationRule r) {
            Sensor s = r.getSensor();
            Actuator a = r.getActuator();
            return new RuleDto(r.getId(), r.getName(), s.getId(), s.getName(), s.getZone().getId(), s.getZone().getName(),
                    r.getCondition(), r.getThreshold(), s.getUnit(), a == null ? null : a.getId(),
                    a == null ? null : a.getName(), r.getDurationMin(), r.getSeverity(), r.isActive(),
                    r.getCooldownMinutes(), r.getLastTriggeredAt());
        }
    }

    public record TaskDto(Long id, String name, Long actuatorId, String actuatorName, Long zoneId, String zoneName,
                          LocalTime runTime, Set<DayOfWeek> repeatDays, int durationMin, boolean active,
                          LocalDate lastRunDate) {
        static TaskDto of(ScheduledTask t) {
            Actuator a = t.getActuator();
            return new TaskDto(t.getId(), t.getName(), a.getId(), a.getName(), a.getZone().getId(), a.getZone().getName(),
                    t.getRunTime(), t.getRepeatDays(), t.getDurationMin(), t.isActive(), t.getLastRunDate());
        }
    }

    public record AlertDto(Long id, String message, Severity severity, AlertStatus status, Instant raisedAt,
                           Long zoneId, Long ruleId, boolean delivered, String acknowledgedBy, Instant acknowledgedAt) {
        static AlertDto of(Alert a) {
            return new AlertDto(a.getId(), a.getMessage(), a.getSeverity(), a.getStatus(), a.getRaisedAt(),
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

    public record ZoneRequest(@NotBlank String type, @NotBlank @Size(max = 80) String name,
                              String cropName, @PositiveOrZero Integer birdCount) {
    }

    public record SensorRequest(@NotNull SensorType type, @NotBlank String name, Boolean simulated) {
    }

    public record ActuatorRequest(@NotNull ActuatorType type, @NotBlank String name,
                                  @Positive @Max(240) Integer maxRunMinutes) {
    }

    public record RuleRequest(@NotBlank String name, @NotNull Long sensorId, @NotNull Condition condition,
                              @NotNull Double threshold, Long actuatorId, @PositiveOrZero int durationMin,
                              @NotNull Severity severity, @Positive Integer cooldownMinutes) {
    }

    public record TaskRequest(@NotBlank String name, @NotNull Long actuatorId, @NotNull LocalTime runTime,
                              @NotEmpty Set<DayOfWeek> repeatDays, @Positive int durationMin) {
    }

    public record ActivateRequest(@Positive int durationMin) {
    }

    public record ActiveRequest(boolean active) {
    }

    public record RepairRequest(boolean testPassed) {
    }
}
