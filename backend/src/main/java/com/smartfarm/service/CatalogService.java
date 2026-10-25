package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.DeviceModelRepository;
import com.smartfarm.repository.FarmDeviceRepository;
import com.smartfarm.repository.MeasurementTypeRepository;
import com.smartfarm.repository.ModelPictureRepository;
import com.smartfarm.repository.SensorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Master data: measurement types and device models. Everyone may read the catalog; the Farm Owner and the
 * Technician manage it. Built-in entries can be edited but not deleted; a model in use is retired instead.
 */
@Service
@Transactional
public class CatalogService {

    public record ModelCommand(String name, String manufacturer, String category, String description, String interfaceType,
                               String voltage, Double priceUsd, String datasheetUrl, String wiringNotes, String wokwiPart,
                               String artType, List<ModelChannel> channels, String actuatorType, String actionVerb,
                               Integer defaultMaxRunMinutes, List<ModelEffect> effects) {
    }

    private final MeasurementTypeRepository measurements;
    private final DeviceModelRepository models;
    private final ModelPictureRepository pictures;
    private final FarmDeviceRepository devices;
    private final SensorRepository sensors;
    private final AccessPolicy access;

    public CatalogService(MeasurementTypeRepository measurements, DeviceModelRepository models, ModelPictureRepository pictures,
                          FarmDeviceRepository devices, SensorRepository sensors, AccessPolicy access) {
        this.measurements = measurements;
        this.models = models;
        this.pictures = pictures;
        this.devices = devices;
        this.sensors = sensors;
        this.access = access;
    }

    // ---------- measurement types ----------

    @Transactional(readOnly = true)
    public List<MeasurementType> measurements() {
        return measurements.findAllByOrderByNameAsc();
    }

    public MeasurementType createMeasurement(Long userId, String name, String unit, double min, double max, double typical) {
        access.require(userId, Role.TECHNICIAN);
        String code = MeasurementType.codeFrom(name);
        if (measurements.existsByCode(code)) {
            throw new IllegalArgumentException("A measurement called " + name + " already exists");
        }
        return measurements.save(new MeasurementType(code, name.trim(), unit.trim(), min, max, typical, false));
    }

    /** A built-in measurement keeps its unit and range: its sensor class checks readings with them. */
    public MeasurementType updateMeasurement(Long userId, Long id, String name, String unit, double min, double max, double typical) {
        access.require(userId, Role.TECHNICIAN);
        MeasurementType m = measurement(id);
        if (m.isBuiltIn() && (!m.getUnit().equals(unit) || m.getMinValid() != min || m.getMaxValid() != max)) {
            throw new IllegalArgumentException(m.getName() + " is built in: only its name and typical value can change");
        }
        m.update(name.trim(), unit.trim(), min, max, typical);
        return m;
    }

    public void deleteMeasurement(Long userId, Long id) {
        access.require(userId, Role.TECHNICIAN);
        MeasurementType m = measurement(id);
        if (m.isBuiltIn()) {
            throw new IllegalArgumentException(m.getName() + " is built in and cannot be deleted");
        }
        boolean usedByModel = models.findAll().stream().anyMatch(dm ->
                dm.getChannels().stream().anyMatch(c -> c.getMeasurementCode().equals(m.getCode()))
                        || dm.getEffects().stream().anyMatch(e -> e.getMeasurementCode().equals(m.getCode())));
        boolean usedBySensor = sensors.findAll().stream().anyMatch(s -> s.getMeasurementCode().equals(m.getCode()));
        if (usedByModel || usedBySensor) {
            throw new IllegalArgumentException(m.getName() + " is used by a device model or a sensor");
        }
        measurements.delete(m);
    }

    // ---------- device models ----------

    @Transactional(readOnly = true)
    public List<DeviceModel> models() {
        return models.findAllByOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public boolean inUse(Long modelId) {
        return devices.existsByModel_Id(modelId);
    }

    public DeviceModel createModel(Long userId, ModelCommand cmd) {
        access.require(userId, Role.TECHNICIAN);
        if (models.existsByNameIgnoreCase(cmd.name().trim())) {
            throw new IllegalArgumentException("A model called " + cmd.name().trim() + " already exists");
        }
        return models.save(apply(new DeviceModel(cmd.name().trim(), cmd.category(), false), cmd));
    }

    public DeviceModel updateModel(Long userId, Long id, ModelCommand cmd) {
        access.require(userId, Role.TECHNICIAN);
        DeviceModel model = model(id);
        if (!model.getCategory().equals(cmd.category())) {
            throw new IllegalArgumentException("A model's category cannot change; make a new model instead");
        }
        String name = cmd.name().trim();
        if (!model.getName().equalsIgnoreCase(name) && models.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A model called " + name + " already exists");
        }
        model.rename(name);
        return apply(model, cmd);
    }

    private DeviceModel apply(DeviceModel model, ModelCommand cmd) {
        model.describe(cmd.manufacturer(), cmd.description(), cmd.interfaceType(), cmd.voltage(), cmd.priceUsd())
                .looks(cmd.wokwiPart(), cmd.artType())
                .docs(cmd.datasheetUrl(), cmd.wiringNotes());
        List<ModelChannel> channels = cmd.channels() == null ? List.of() : cmd.channels();
        List<ModelEffect> effects = cmd.effects() == null ? List.of() : cmd.effects();
        for (ModelChannel c : channels) {
            requireMeasurement(c.getMeasurementCode());
        }
        for (ModelEffect e : effects) {
            requireMeasurement(e.getMeasurementCode());
        }
        switch (model.getCategory()) {
            case "SENSOR" -> {
                if (channels.isEmpty()) {
                    throw new IllegalArgumentException("A sensor model measures at least one thing");
                }
                model.measures(channels);
            }
            case "ACTUATOR" -> model.acts(cmd.actuatorType(), cmd.actionVerb(), cmd.defaultMaxRunMinutes(), effects);
            default -> model.measures(List.of());
        }
        return model;
    }

    /** Retired models stay for the devices made from them but are not offered for new devices. */
    public DeviceModel setRetired(Long userId, Long id, boolean retired) {
        access.require(userId, Role.TECHNICIAN);
        DeviceModel model = model(id);
        model.setRetired(retired);
        return model;
    }

    public void deleteModel(Long userId, Long id) {
        access.require(userId, Role.TECHNICIAN);
        DeviceModel model = model(id);
        if (model.isBuiltIn()) {
            throw new IllegalArgumentException(model.getName() + " is part of the starter catalog: retire it instead");
        }
        if (devices.existsByModel_Id(id)) {
            throw new IllegalArgumentException(model.getName() + " is used by devices on the farm: retire it instead");
        }
        pictures.findById(id).ifPresent(pictures::delete);
        models.delete(model);
    }

    // ---------- pictures ----------

    public void savePicture(Long userId, Long modelId, String contentType, byte[] bytes) {
        access.require(userId, Role.TECHNICIAN);
        DeviceModel model = model(modelId);
        Optional<ModelPicture> existing = pictures.findById(modelId);
        if (existing.isPresent()) {
            existing.get().replace(contentType, bytes);
        } else {
            pictures.save(new ModelPicture(modelId, contentType, bytes));
        }
        model.markPicture(true);
    }

    public void deletePicture(Long userId, Long modelId) {
        access.require(userId, Role.TECHNICIAN);
        DeviceModel model = model(modelId);
        pictures.findById(modelId).ifPresent(pictures::delete);
        model.markPicture(false);
    }

    @Transactional(readOnly = true)
    public Optional<ModelPicture> picture(Long modelId) {
        return pictures.findById(modelId);
    }

    // ---------- lookups ----------

    @Transactional(readOnly = true)
    public DeviceModel model(Long id) {
        return models.findById(id).orElseThrow(() -> new NotFoundException("Device model", id));
    }

    private MeasurementType measurement(Long id) {
        return measurements.findById(id).orElseThrow(() -> new NotFoundException("Measurement", id));
    }

    private void requireMeasurement(String code) {
        if (code == null || !measurements.existsByCode(code)) {
            throw new IllegalArgumentException("Unknown measurement " + code + ": add it under Measurement types first");
        }
    }
}
