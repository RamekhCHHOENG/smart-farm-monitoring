package com.smartfarm.web;

import com.smartfarm.domain.ModelChannel;
import com.smartfarm.domain.ModelEffect;
import com.smartfarm.domain.ModelPicture;
import com.smartfarm.service.CatalogService;
import com.smartfarm.service.CatalogService.ModelCommand;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/** The device catalog (master data): measurement types and device models with their pictures. */
@RestController
@RequestMapping("/api/catalog")
@Transactional
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    // ---------- measurement types ----------

    @GetMapping("/measurements")
    @Transactional(readOnly = true)
    public List<MeasurementDto> measurements() {
        return catalog.measurements().stream().map(MeasurementDto::of).toList();
    }

    @PostMapping("/measurements")
    @ResponseStatus(HttpStatus.CREATED)
    public MeasurementDto createMeasurement(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
                                            @Valid @RequestBody MeasurementRequest req) {
        return MeasurementDto.of(catalog.createMeasurement(userId, req.name(), req.unit(), req.minValid(), req.maxValid(), req.typical()));
    }

    @PutMapping("/measurements/{id}")
    public MeasurementDto updateMeasurement(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                            @Valid @RequestBody MeasurementRequest req) {
        return MeasurementDto.of(catalog.updateMeasurement(userId, id, req.name(), req.unit(), req.minValid(), req.maxValid(), req.typical()));
    }

    @DeleteMapping("/measurements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMeasurement(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        catalog.deleteMeasurement(userId, id);
    }

    // ---------- device models ----------

    @GetMapping("/models")
    @Transactional(readOnly = true)
    public List<DeviceModelDto> models() {
        return catalog.models().stream().map(m -> DeviceModelDto.of(m, catalog.inUse(m.getId()))).toList();
    }

    @GetMapping("/models/{id}")
    @Transactional(readOnly = true)
    public DeviceModelDto model(@PathVariable Long id) {
        return DeviceModelDto.of(catalog.model(id), catalog.inUse(id));
    }

    @PostMapping("/models")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceModelDto createModel(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody DeviceModelRequest req) {
        return DeviceModelDto.of(catalog.createModel(userId, command(req)), false);
    }

    @PutMapping("/models/{id}")
    public DeviceModelDto updateModel(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                      @Valid @RequestBody DeviceModelRequest req) {
        return DeviceModelDto.of(catalog.updateModel(userId, id, command(req)), catalog.inUse(id));
    }

    @PatchMapping("/models/{id}/retired")
    public DeviceModelDto retire(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                 @RequestBody RetireRequest req) {
        return DeviceModelDto.of(catalog.setRetired(userId, id, req.retired()), catalog.inUse(id));
    }

    @DeleteMapping("/models/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModel(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        catalog.deleteModel(userId, id);
    }

    // ---------- pictures: upload with the login token, show publicly (an <img> cannot send the token) ----------

    @PutMapping(value = "/models/{id}/photo", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp", MediaType.IMAGE_GIF_VALUE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPicture(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                              @RequestHeader("Content-Type") String contentType, @RequestBody byte[] bytes) {
        catalog.savePicture(userId, id, contentType.split(";")[0].trim(), bytes);
    }

    @DeleteMapping("/models/{id}/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePicture(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        catalog.deletePicture(userId, id);
    }

    @GetMapping("/models/{id}/picture")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> picture(@PathVariable Long id) {
        return catalog.picture(id)
                .map((ModelPicture p) -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                        .body(p.getBytes()))
                .orElse(ResponseEntity.notFound().build());
    }

    private static ModelCommand command(DeviceModelRequest r) {
        List<ModelChannel> channels = r.channels() == null ? List.of()
                : r.channels().stream().map(c -> new ModelChannel(c.measurementCode(), c.label().trim())).toList();
        List<ModelEffect> effects = r.effects() == null ? List.of()
                : r.effects().stream().map(e -> new ModelEffect(e.measurementCode(), e.direction())).toList();
        String actuatorType = r.actuatorType() == null || r.actuatorType().isBlank() || r.actuatorType().equals("GENERIC")
                ? null : r.actuatorType();
        return new ModelCommand(r.name(), r.manufacturer(), r.category(), r.description(), r.interfaceType(), r.voltage(),
                r.priceUsd(), r.datasheetUrl(), r.wiringNotes(), r.wokwiPart(), r.artType(), channels, actuatorType,
                r.actionVerb(), r.defaultMaxRunMinutes(), effects);
    }
}
