package com.smartfarm.web;

import com.smartfarm.config.DeviceKey;
import com.smartfarm.service.ActuatorService;
import com.smartfarm.service.ForbiddenException;
import com.smartfarm.service.MonitoringService;
import com.smartfarm.service.MonitoringService.ReadingResult;
import com.smartfarm.web.Dtos.ActuatorStateDto;
import com.smartfarm.web.Dtos.ReadingRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GRASP Controller: the system-event boundary for devices (sensor nodes / ESP32).
 * It only checks the device key and delegates; no farm logic lives here.
 */
@RestController
@RequestMapping("/api/devices")
public class ReadingController {

    private final MonitoringService monitoringService;
    private final ActuatorService actuatorService;
    private final DeviceKey deviceKey;

    public ReadingController(MonitoringService monitoringService, ActuatorService actuatorService, DeviceKey deviceKey) {
        this.monitoringService = monitoringService;
        this.actuatorService = actuatorService;
        this.deviceKey = deviceKey;
    }

    /** UC-3 Submit Sensor Reading. 201 when stored, 202 when the sensor is marked faulty, 422 when the value is invalid. */
    @PostMapping("/readings")
    public ResponseEntity<ReadingResult> submitReading(@RequestHeader("X-Device-Key") String key,
                                                       @Valid @RequestBody ReadingRequest request) {
        checkKey(key);
        ReadingResult result = monitoringService.processReading(request.sensorId(), request.value());
        HttpStatus status = result.hasInvalidValue() ? HttpStatus.UNPROCESSABLE_CONTENT
                : result.isIgnored() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    /** Polled by an ESP32 to know whether to switch its relay on. */
    @GetMapping("/actuators/{id}/state")
    public ActuatorStateDto actuatorState(@RequestHeader("X-Device-Key") String key, @PathVariable Long id) {
        checkKey(key);
        return ActuatorStateDto.of(actuatorService.get(id));
    }

    private void checkKey(String key) {
        if (!deviceKey.matches(key)) {
            throw new ForbiddenException("Invalid device key");
        }
    }
}
