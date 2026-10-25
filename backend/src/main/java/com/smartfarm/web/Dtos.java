package com.smartfarm.web;

import com.smartfarm.domain.*;
import jakarta.validation.Valid;
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

    /** role is the farm role (null for company staff); companyRole is ADMIN or SUPPORT for company staff. */
    public record UserDto(Long id, String username, String name, Role role, String companyRole, Long farmId) {
        static UserDto of(AppUser u) {
            // getId() on the farm reference does not load the farm, so this is safe outside a transaction
            return new UserDto(u.getId(), u.getUsername(), u.getName(), u.getRole(), u.getCompanyRole(),
                    u.getFarm() == null ? null : u.getFarm().getId());
        }
    }

    public record ReadingDto(Long id, Long sensorId, double value, Instant recordedAt) {
        static ReadingDto of(SensorReading r) {
            return new ReadingDto(r.getId(), r.getSensorId(), r.getValue(), r.getRecordedAt());
        }
    }

    /**
     * {@code type} is the measurement code (SOIL_MOISTURE... or a catalog code like CO2); {@code status} is what
     * staff see; {@code repairStartedAt} is set while the sensor is in MAINTENANCE; {@code deviceId} is its unit.
     */
    public record SensorDto(Long id, Long zoneId, String name, String type, String measurementName, String unit, double minValid,
                            double maxValid, boolean simulated, boolean online, boolean suspect, SensorStatus status,
                            Instant repairStartedAt, Instant lastSeenAt, ReadingDto latest, Long deviceId, double typical) {
        static SensorDto of(Sensor s, SensorReading latest) {
            return new SensorDto(s.getId(), s.getZone().getId(), s.getName(), s.getMeasurementCode(), s.getMeasurementName(),
                    s.getUnit(), s.getMinValid(), s.getMaxValid(), s.isSimulated(), s.isOnline(), s.isSuspect(), s.getStatus(),
                    s.getRepairStartedAt(), s.getLastSeenAt(), latest == null ? null : ReadingDto.of(latest),
                    s.getDevice() == null ? null : s.getDevice().getId(), s.initialSimulatedValue());
        }
    }

    public record EffectDto(String measurementCode, int direction) {
        static EffectDto of(ModelEffect e) {
            return new EffectDto(e.getMeasurementCode(), e.getDirection());
        }
    }

    public record ChannelDto(String measurementCode, String label) {
        static ChannelDto of(ModelChannel c) {
            return new ChannelDto(c.getMeasurementCode(), c.getLabel());
        }
    }

    /**
     * {@code type}: WATER_PUMP, FAN... or GENERIC for a catalog kind ({@code kindName} names it);
     * {@code startedByRuleId} is set while a rule's run is going; {@code effects}: what it does while it runs.
     */
    public record ActuatorDto(Long id, Long zoneId, String name, String type, String kindName, ActuatorStatus status,
                              int maxRunMinutes, Instant runningUntil, Long startedByRuleId, Long deviceId,
                              List<EffectDto> effects) {
        static ActuatorDto of(Actuator a) {
            return new ActuatorDto(a.getId(), a.getZone().getId(), a.getName(), a.getKindCode(), a.getKindName(), a.getStatus(),
                    a.getMaxRunMinutes(), a.getRunningUntil(), a.getStartedByRuleId(),
                    a.getDevice() == null ? null : a.getDevice().getId(), a.getEffects().stream().map(EffectDto::of).toList());
        }
    }

    /** A physical unit, with its model's look so a list can draw it without loading the catalog. */
    public record FarmDeviceDto(Long id, Long zoneId, String name, Long modelId, String modelName, String category,
                                String wokwiPart, String artType, boolean hasPicture, Long controllerId, String controllerName,
                                String serialNumber, String notes, Instant installedAt, List<Long> sensorIds, List<Long> actuatorIds) {
        static FarmDeviceDto of(FarmDevice d) {
            DeviceModel m = d.getModel();
            return new FarmDeviceDto(d.getId(), d.getZone().getId(), d.getName(), m.getId(), m.getName(), m.getCategory(),
                    m.getWokwiPart(), m.getArtType(), m.isHasPicture(),
                    d.getController() == null ? null : d.getController().getId(),
                    d.getController() == null ? null : d.getController().getName(),
                    d.getSerialNumber(), d.getNotes(), d.getInstalledAt(),
                    d.getSensors().stream().map(Sensor::getId).toList(), d.getActuators().stream().map(Actuator::getId).toList());
        }
    }

    public record MeasurementDto(Long id, String code, String name, String unit, double minValid, double maxValid,
                                 double typical, boolean builtIn) {
        static MeasurementDto of(MeasurementType m) {
            return new MeasurementDto(m.getId(), m.getCode(), m.getName(), m.getUnit(), m.getMinValid(), m.getMaxValid(),
                    m.getTypical(), m.isBuiltIn());
        }
    }

    public record DeviceModelDto(Long id, String name, String manufacturer, String category, String description,
                                 String interfaceType, String voltage, Double priceUsd, String datasheetUrl, String wiringNotes,
                                 String wokwiPart, String artType, List<ChannelDto> channels, String actuatorType,
                                 String actionVerb, Integer defaultMaxRunMinutes, List<EffectDto> effects,
                                 boolean hasPicture, boolean builtIn, boolean retired, boolean inUse) {
        static DeviceModelDto of(DeviceModel m, boolean inUse) {
            return new DeviceModelDto(m.getId(), m.getName(), m.getManufacturer(), m.getCategory(), m.getDescription(),
                    m.getInterfaceType(), m.getVoltage(), m.getPriceUsd(), m.getDatasheetUrl(), m.getWiringNotes(),
                    m.getWokwiPart(), m.getArtType(), m.getChannels().stream().map(ChannelDto::of).toList(),
                    m.getActuatorType(), m.getActionVerb(), m.getDefaultMaxRunMinutes(),
                    m.getEffects().stream().map(EffectDto::of).toList(), m.isHasPicture(), m.isBuiltIn(), m.isRetired(), inUse);
        }
    }

    public record ControllerDto(Long id, String name, Long modelId, String modelName, String wokwiPart, String keyHint,
                                String notes, Instant lastSeenAt, long deviceCount) {
        static ControllerDto of(ControllerBoard b, long deviceCount) {
            DeviceModel m = b.getModel();
            return new ControllerDto(b.getId(), b.getName(), m == null ? null : m.getId(), m == null ? null : m.getName(),
                    m == null ? null : m.getWokwiPart(), b.getKeyHint(), b.getNotes(), b.getLastSeenAt(), deviceCount);
        }
    }

    /** Returned once, when a board is made or gets a new key: the key is never shown again. */
    public record ControllerKeyDto(ControllerDto board, String key, String firmware) {
    }

    /** {@code lab}: a Farm Lab test zone (its alerts stay in the lab). */
    public record ZoneDto(Long id, String name, String type, String detail, String cropName, Integer birdCount,
                          boolean lab, List<SensorDto> sensors, List<ActuatorDto> actuators, List<FarmDeviceDto> devices) {
    }

    public record FarmDto(Long id, String name, String location, String timeZone, long openAlerts, List<ZoneDto> zones,
                          String status, boolean supportAllowed) {
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
                           Long zoneId, Long ruleId, boolean delivered, String acknowledgedBy, Instant acknowledgedAt, boolean lab) {
        static AlertDto of(Alert a) {
            return new AlertDto(a.getId(), a.getMessage(), a.getSeverity(), a.getStatus(), a.getRaisedAt(),
                    a.getRepeatCount(), a.getLastRaisedAt(),
                    a.getZoneId(), a.getRuleId(), a.isDelivered(), a.getAcknowledgedBy(), a.getAcknowledgedAt(), a.isLab());
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
                              String cropName, @PositiveOrZero Integer birdCount, Boolean lab) {
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

    public record MeasurementRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 20) String unit,
                                     @NotNull Double minValid, @NotNull Double maxValid, @NotNull Double typical) {
    }

    public record ChannelRequest(@NotBlank String measurementCode, @NotBlank @Size(max = 40) String label) {
    }

    public record EffectRequest(@NotBlank String measurementCode, @NotNull Integer direction) {
    }

    public record DeviceModelRequest(@NotBlank @Size(max = 120) String name, @Size(max = 80) String manufacturer,
                                     @NotBlank String category, @Size(max = 1000) String description,
                                     @Size(max = 60) String interfaceType, @Size(max = 40) String voltage,
                                     @PositiveOrZero Double priceUsd, @Size(max = 500) String datasheetUrl,
                                     @Size(max = 1000) String wiringNotes, @Size(max = 60) String wokwiPart,
                                     @Size(max = 40) String artType, List<@Valid ChannelRequest> channels,
                                     String actuatorType, @Size(max = 40) String actionVerb,
                                     @Positive @Max(240) Integer defaultMaxRunMinutes, List<@Valid EffectRequest> effects) {
    }

    public record FarmDeviceRequest(@NotNull Long modelId, @NotBlank @Size(max = 80) String name, Boolean simulated,
                                    @Positive @Max(240) Integer maxRunMinutes, Long controllerId,
                                    @Size(max = 80) String serialNumber, @Size(max = 1000) String notes) {
    }

    public record FarmDeviceUpdateRequest(@NotBlank @Size(max = 80) String name, @Size(max = 80) String serialNumber,
                                          @Size(max = 1000) String notes, Long controllerId) {
    }

    public record ControllerRequest(@NotBlank @Size(max = 80) String name, Long modelId, @Size(max = 500) String notes) {
    }

    public record RetireRequest(boolean retired) {
    }

    /** Boolean, not boolean: actions other than COMPLETE_REPAIR send an empty body {}. */
    public record RepairRequest(Boolean testPassed) {
    }
}
