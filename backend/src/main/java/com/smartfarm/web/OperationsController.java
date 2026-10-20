package com.smartfarm.web;

import com.smartfarm.domain.AlertStatus;
import com.smartfarm.service.ActuatorService;
import com.smartfarm.service.ActuatorService.LifecycleAction;
import com.smartfarm.service.AlertService;
import com.smartfarm.service.QueryService;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Manual control (UC-6), technician lifecycle, alerts, and history (UC-8). */
@RestController
@RequestMapping("/api")
@Transactional
public class OperationsController {

    private final ActuatorService actuatorService;
    private final AlertService alertService;
    private final QueryService queries;

    public OperationsController(ActuatorService actuatorService, AlertService alertService, QueryService queries) {
        this.actuatorService = actuatorService;
        this.alertService = alertService;
        this.queries = queries;
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
        return ActuatorDto.of(actuatorService.lifecycle(userId, id, action, req != null && req.testPassed()));
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
