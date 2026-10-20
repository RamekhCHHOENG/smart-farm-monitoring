package com.smartfarm.web;

import com.smartfarm.domain.*;
import com.smartfarm.repository.AlertRepository;
import com.smartfarm.service.FarmService;
import com.smartfarm.service.QueryService;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.List;

/** Dashboard (UC-7), zones (UC-1) and device registration (UC-2). */
@RestController
@RequestMapping("/api")
public class FarmController {

    private final FarmService farmService;
    private final QueryService queries;
    private final AlertRepository alerts;
    private final Clock clock;

    public FarmController(FarmService farmService, QueryService queries, AlertRepository alerts, Clock clock) {
        this.farmService = farmService;
        this.queries = queries;
        this.alerts = alerts;
        this.clock = clock;
    }

    @GetMapping("/farm")
    @Transactional(readOnly = true)
    public FarmDto farm() {
        Farm farm = farmService.farm();
        return new FarmDto(farm.getId(), farm.getName(), farm.getLocation(), clock.getZone().getId(),
                alerts.countByStatus(AlertStatus.OPEN), farm.getZones().stream().map(this::toDto).toList());
    }

    @PutMapping("/farm")
    @Transactional
    public FarmDto updateFarm(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody FarmRequest req) {
        farmService.updateFarm(userId, req.name(), req.location());
        return farm();
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ZoneDto createZone(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody ZoneRequest req) {
        return toDto(farmService.createZone(userId, req.type(), req.name(), req.cropName(), req.birdCount()));
    }

    @PutMapping("/zones/{id}")
    @Transactional
    public ZoneDto updateZone(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                              @Valid @RequestBody ZoneRequest req) {
        return toDto(farmService.updateZone(userId, id, req.name(), req.cropName(), req.birdCount()));
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        farmService.deleteZone(userId, id);
    }

    @PostMapping("/zones/{id}/sensors")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SensorDto addSensor(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                               @Valid @RequestBody SensorRequest req) {
        Sensor sensor = farmService.addSensor(userId, id, req.type(), req.name(), req.simulated() == null || req.simulated());
        return SensorDto.of(sensor, null);
    }

    @PostMapping("/zones/{id}/actuators")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ActuatorDto addActuator(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                   @Valid @RequestBody ActuatorRequest req) {
        return ActuatorDto.of(farmService.addActuator(userId, id, req.type(), req.name(), req.maxRunMinutes()));
    }

    @PostMapping("/zones/{id}/default-rules")
    @Transactional
    public List<RuleDto> applyDefaultRules(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return farmService.applyDefaultRules(userId, id).stream().map(RuleDto::of).toList();
    }

    @DeleteMapping("/sensors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSensor(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        farmService.removeSensor(userId, id);
    }

    @DeleteMapping("/actuators/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeActuator(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        farmService.removeActuator(userId, id);
    }

    private ZoneDto toDto(Zone zone) {
        List<SensorDto> sensors = zone.getSensors().stream()
                .map(s -> SensorDto.of(s, queries.latest(s).orElse(null)))
                .toList();
        List<ActuatorDto> actuators = zone.getActuators().stream().map(ActuatorDto::of).toList();
        return new ZoneDto(zone.getId(), zone.getName(), zone.getZoneType(), zone.getDetail(),
                zone instanceof CropZone c ? c.getCropName() : null,
                zone instanceof PoultryZone p ? p.getBirdCount() : null,
                sensors, actuators);
    }
}
