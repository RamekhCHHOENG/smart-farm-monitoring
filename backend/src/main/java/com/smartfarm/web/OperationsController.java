package com.smartfarm.web;

import com.smartfarm.domain.AlertStatus;
import com.smartfarm.domain.Sensor;
import com.smartfarm.domain.SensorReading;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.smartfarm.service.ActuatorService;
import com.smartfarm.service.ActuatorService.LifecycleAction;
import com.smartfarm.service.AlertService;
import com.smartfarm.service.QueryService;
import com.smartfarm.service.SensorService;
import com.smartfarm.service.SensorService.SensorAction;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;

/** Manual control (UC-6), technician lifecycle, alerts, and history (UC-8). */
@RestController
@RequestMapping("/api")
@Transactional
public class OperationsController {

    private final ActuatorService actuatorService;
    private final SensorService sensorService;
    private final AlertService alertService;
    private final QueryService queries;
    private final Clock clock;

    public OperationsController(ActuatorService actuatorService, SensorService sensorService, AlertService alertService,
                                QueryService queries, Clock clock) {
        this.actuatorService = actuatorService;
        this.sensorService = sensorService;
        this.alertService = alertService;
        this.queries = queries;
        this.clock = clock;
    }

    @PostMapping("/actuators/{id}/on")
    public ActuatorDto turnOn(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                              @Valid @RequestBody ActivateRequest req) {
        return ActuatorDto.of(actuatorService.turnOn(userId, id, req.durationMin()));
    }

    @PostMapping("/actuators/{id}/off")
    public ActuatorDto turnOff(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return ActuatorDto.of(actuatorService.turnOff(userId, id));
    }

    /** action = FAULT | OFFLINE | RESTORE | START_REPAIR | COMPLETE_REPAIR | DECOMMISSION */
    @PostMapping("/actuators/{id}/lifecycle/{action}")
    public ActuatorDto lifecycle(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                 @PathVariable LifecycleAction action,
                                 @RequestBody(required = false) RepairRequest req) {
        return ActuatorDto.of(actuatorService.lifecycle(userId, id, action, req != null && Boolean.TRUE.equals(req.testPassed())));
    }

    /**
     * Technician: action = FAULT | START_REPAIR | COMPLETE_REPAIR. COMPLETE_REPAIR runs the sensor's
     * test; when it fails the answer still has status MAINTENANCE (the reason is in the zone's device log).
     */
    @PostMapping("/sensors/{id}/lifecycle/{action}")
    public SensorDto sensorLifecycle(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                     @PathVariable SensorAction action) {
        Sensor sensor = sensorService.lifecycle(userId, id, action);
        return SensorDto.of(sensor, queries.latest(sensor).orElse(null));
    }

    @GetMapping("/actuators/{id}/logs")
    @Transactional(readOnly = true)
    public List<DeviceLogDto> actuatorLogs(@PathVariable Long id) {
        return actuatorService.logs(id).stream().map(DeviceLogDto::of).toList();
    }

    @GetMapping("/alerts")
    @Transactional(readOnly = true)
    public List<AlertDto> alerts(@RequestParam(required = false) AlertStatus status) {
        return alertService.list(status).stream().map(AlertDto::of).toList();
    }

    public record CountDto(int count) {
    }

    @PostMapping("/alerts/acknowledge-all")
    public CountDto acknowledgeAll(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return new CountDto(alertService.acknowledgeAll(userId));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public AlertDto acknowledge(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return AlertDto.of(alertService.acknowledge(userId, id));
    }

    /** FR-10: readings of one sensor in a date range (defaults to the last 24 hours). */
    @GetMapping("/sensors/{id}/readings")
    @Transactional(readOnly = true)
    public List<ReadingDto> readings(@PathVariable Long id,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Range r = Range.of(from, to);
        return queries.history(id, r.from(), r.to()).stream().map(ReadingDto::of).toList();
    }

    /** FR-10: the same readings as a spreadsheet file, every reading (not thinned), in farm-local time. */
    @GetMapping(value = "/sensors/{id}/readings.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<String> readingsCsv(@PathVariable Long id,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Range r = Range.of(from, to);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(clock.getZone());
        StringBuilder csv = new StringBuilder("time,value\n");
        for (SensorReading reading : queries.allReadings(id, r.from(), r.to())) {
            csv.append(format.format(reading.getRecordedAt())).append(',').append(reading.getValue()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sensor-" + id + "-readings.csv\"")
                .body(csv.toString());
    }

    /** FR-10: device activity log of one zone in a date range. */
    @GetMapping("/zones/{id}/logs")
    @Transactional(readOnly = true)
    public List<DeviceLogDto> zoneLogs(@PathVariable Long id,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Range r = Range.of(from, to);
        return queries.zoneLogs(id, r.from(), r.to()).stream().map(DeviceLogDto::of).toList();
    }

    private record Range(Instant from, Instant to) {
        static Range of(Instant from, Instant to) {
            Instant end = to != null ? to : Instant.now();
            Instant start = from != null ? from : end.minus(Duration.ofDays(1));
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("'from' must be before 'to'");
            }
            return new Range(start, end);
        }
    }
}
