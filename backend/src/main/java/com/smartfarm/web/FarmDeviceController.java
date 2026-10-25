package com.smartfarm.web;

import com.smartfarm.service.FarmDeviceService;
import com.smartfarm.web.Dtos.FarmDeviceDto;
import com.smartfarm.web.Dtos.FarmDeviceRequest;
import com.smartfarm.web.Dtos.FarmDeviceUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Physical devices made from catalog models. Not under /api/devices, which is the device key API for boards.
 */
@RestController
@RequestMapping("/api")
@Transactional
public class FarmDeviceController {

    private final FarmDeviceService devices;

    public FarmDeviceController(FarmDeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/zones/{zoneId}/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public FarmDeviceDto install(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long zoneId,
                                 @Valid @RequestBody FarmDeviceRequest req) {
        return FarmDeviceDto.of(devices.install(userId, zoneId, req.modelId(), req.name(), req.simulated() == null || req.simulated(),
                req.maxRunMinutes(), req.controllerId(), req.serialNumber(), req.notes()));
    }

    @PutMapping("/farm-devices/{id}")
    public FarmDeviceDto update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                @Valid @RequestBody FarmDeviceUpdateRequest req) {
        return FarmDeviceDto.of(devices.update(userId, id, req.name(), req.serialNumber(), req.notes(), req.controllerId()));
    }

    @DeleteMapping("/farm-devices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        devices.remove(userId, id);
    }
}
