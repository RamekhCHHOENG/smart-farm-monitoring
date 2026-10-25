package com.smartfarm.web;

import com.smartfarm.service.LabService;
import com.smartfarm.service.MonitoringService;
import com.smartfarm.service.MonitoringService.ReadingResult;
import com.smartfarm.web.Dtos.ActuatorStateDto;
import com.smartfarm.web.Dtos.ReadingRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The device API for the Farm Lab's virtual devices: the same two calls as an ESP32 board, authenticated by the
 * signed-in technician or owner instead of the device key, and only for devices in a lab zone.
 */
@RestController
@RequestMapping("/api/lab")
public class LabController {

    private final LabService labService;
    private final MonitoringService monitoringService;

    public LabController(LabService labService, MonitoringService monitoringService) {
        this.labService = labService;
        this.monitoringService = monitoringService;
    }

    @PostMapping("/readings")
    public ResponseEntity<ReadingResult> submitReading(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
                                                       @Valid @RequestBody ReadingRequest request) {
        labService.labSensor(userId, request.sensorId());
        ReadingResult result = monitoringService.processReading(request.sensorId(), request.value());
        return ResponseEntity.status(ReadingController.httpStatus(result)).body(result);
    }

    @GetMapping("/actuators/{id}/state")
    public ActuatorStateDto actuatorState(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return ActuatorStateDto.of(labService.labActuator(userId, id));
    }
}
