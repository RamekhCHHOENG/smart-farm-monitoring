package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Installing, editing and removing physical devices made from catalog models (Technician). */
@Service
@Transactional
public class FarmDeviceService {

    private final ZoneRepository zones;
    private final FarmDeviceRepository devices;
    private final DeviceModelRepository models;
    private final MeasurementTypeRepository measurements;
    private final ControllerBoardRepository boards;
    private final AccessPolicy access;
    private final Clock clock;

    private final Tenancy tenancy;


    public FarmDeviceService(ZoneRepository zones, FarmDeviceRepository devices, DeviceModelRepository models,
                             MeasurementTypeRepository measurements, ControllerBoardRepository boards,
                             AccessPolicy access, Clock clock, Tenancy tenancy) {

        this.tenancy = tenancy;
        this.zones = zones;
        this.devices = devices;
        this.models = models;
        this.measurements = measurements;
        this.boards = boards;
        this.access = access;
        this.clock = clock;
    }

    public FarmDevice install(Long userId, Long zoneId, Long modelId, String name, boolean simulated, Integer maxRunMinutes,
                              Long controllerId, String serialNumber, String notes) {
        access.require(userId, Role.TECHNICIAN);
        Zone zone = tenancy.zone(zones.findById(zoneId).orElseThrow(() -> new NotFoundException("Zone", zoneId)));
        DeviceModel model = models.findById(modelId).orElseThrow(() -> new NotFoundException("Device model", modelId));
        if (model.getFarmId() != null) {
            tenancy.check(model, model.getFarmId(), "Device model", modelId); // another farm's own model
        }
        Map<String, MeasurementType> byCode = new HashMap<>();
        for (ModelChannel channel : model.getChannels()) {
            measurements.findByCode(channel.getMeasurementCode()).ifPresent(m -> byCode.put(m.getCode(), m));
        }
        FarmDevice device = zone.addDevice(model, name.trim(), simulated, maxRunMinutes, byCode, clock.instant());
        device.update(name.trim(), blankToNull(serialNumber), blankToNull(notes), board(controllerId));
        zones.flush();
        return device;
    }

    public FarmDevice update(Long userId, Long deviceId, String name, String serialNumber, String notes, Long controllerId) {
        access.require(userId, Role.TECHNICIAN);
        FarmDevice device = device(deviceId);
        device.update(name.trim(), blankToNull(serialNumber), blankToNull(notes), board(controllerId));
        return device;
    }

    /** Removes the device with its readings and outputs (their rules, schedules and readings go too). */
    public void remove(Long userId, Long deviceId) {
        access.require(userId, Role.TECHNICIAN);
        FarmDevice device = device(deviceId);
        device.getZone().removeDevice(device);
    }

    @Transactional(readOnly = true)
    public FarmDevice device(Long deviceId) {
        return tenancy.device(devices.findById(deviceId).orElseThrow(() -> new NotFoundException("Device", deviceId)));
    }

    private ControllerBoard board(Long controllerId) {
        return controllerId == null ? null
                : tenancy.board(boards.findById(controllerId).orElseThrow(() -> new NotFoundException("Controller board", controllerId)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
