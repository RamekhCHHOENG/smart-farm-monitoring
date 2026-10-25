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

    private final Tenancy tenancy;

    public CatalogService(MeasurementTypeRepository measurements, DeviceModelRepository models, ModelPictureRepository pictures,
                          FarmDeviceRepository devices, SensorRepository sensors, AccessPolicy access, Tenancy tenancy) {
        this.tenancy = tenancy;
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
        return measurements.findAllByOrderByNameAsc().stream().filter(m -> visible(m.getFarmId())).toList();
    }

    public MeasurementType createMeasurement(Long userId, String name, String unit, double min, double max, double typical) {
        requireEditor(userId);
        String code = MeasurementType.codeFrom(name);
        if (measurements.existsByCode(code)) {
            throw new IllegalArgumentException("A measurement called " + name + " already exists");
        }
        MeasurementType created = new MeasurementType(code, name.trim(), unit.trim(), min, max, typical, false);
        created.setFarmId(newEntryFarm());
        return measurements.save(created);
    }

    /** A built-in measurement keeps its unit and range: its sensor class checks readings with them. */
    public MeasurementType updateMeasurement(Long userId, Long id, String name, String unit, double min, double max, double typical) {
        requireEditor(userId);
        MeasurementType m = measurement(id);
        requireCanChange(m.getFarmId(), "Measurement", id);
        if (m.isBuiltIn() && (!m.getUnit().equals(unit) || m.getMinValid() != min || m.getMaxValid() != max)) {
            throw new IllegalArgumentException(m.getName() + " is built in: only its name and typical value can change");
        }
        m.update(name.trim(), unit.trim(), min, max, typical);
        return m;
    }

    public void deleteMeasurement(Long userId, Long id) {
        requireEditor(userId);
        MeasurementType m = measurement(id);
        requireCanChange(m.getFarmId(), "Measurement", id);
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
        return models.findAllByOrderByCategoryAscNameAsc().stream().filter(m -> visible(m.getFarmId())).toList();
    }

    @Transactional(readOnly = true)
    public boolean inUse(Long modelId) {
        return devices.existsByModel_Id(modelId);
    }

    public DeviceModel createModel(Long userId, ModelCommand cmd) {
        requireEditor(userId);
        if (models.existsByNameIgnoreCase(cmd.name().trim())) {
            throw new IllegalArgumentException("A model called " + cmd.name().trim() + " already exists");
        }
        DeviceModel model = apply(new DeviceModel(cmd.name().trim(), cmd.category(), false), cmd);
        model.setFarmId(newEntryFarm());
        return models.save(model);
    }

    public DeviceModel updateModel(Long userId, Long id, ModelCommand cmd) {
        requireEditor(userId);
        DeviceModel model = model(id);
        requireCanChange(model.getFarmId(), "Device model", id);
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
        requireEditor(userId);
        DeviceModel model = model(id);
        requireCanChange(model.getFarmId(), "Device model", id);
        model.setRetired(retired);
        return model;
    }

    public void deleteModel(Long userId, Long id) {
        requireEditor(userId);
        DeviceModel model = model(id);
        requireCanChange(model.getFarmId(), "Device model", id);
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
        requireEditor(userId);
        DeviceModel model = model(modelId);
        requireCanChange(model.getFarmId(), "Device model", modelId);
        Optional<ModelPicture> existing = pictures.findById(modelId);
        if (existing.isPresent()) {
            existing.get().replace(contentType, bytes);
        } else {
            pictures.save(new ModelPicture(modelId, contentType, bytes));
        }
        model.markPicture(true);
    }

    public void deletePicture(Long userId, Long modelId) {
        requireEditor(userId);
        DeviceModel model = model(modelId);
        requireCanChange(model.getFarmId(), "Device model", modelId);
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
        DeviceModel model = models.findById(id).orElseThrow(() -> new NotFoundException("Device model", id));
        if (!visible(model.getFarmId())) {
            throw new NotFoundException("Device model", id); // another farm's own model
        }
        return model;
    }

    // ---------- shared catalog vs a farm's own entries ----------

    /** Company staff keep the catalog from anywhere; on a farm, its technician and owner add the farm's own entries. */
    private void requireEditor(Long userId) {
        if (tenancy.current().map(c -> c.companyRole() != null).orElse(false)) {
            access.requireCompany(userId, false);
        } else {
            access.require(userId, Role.TECHNICIAN);
        }
    }

    /** A farm sees the shared catalog and its own entries; company staff on company pages see the shared catalog. */
    private boolean visible(Long ownerFarmId) {
        return ownerFarmId == null || !tenancy.limited() || ownerFarmId.equals(tenancy.farmId());
    }

    /** What a new entry belongs to: added by a farm's own people it is that farm's; added by company staff it is shared. */
    private Long newEntryFarm() {
        return tenancy.current().filter(c -> c.companyRole() == null && c.farmId() != null).map(Tenancy.Context::farmId).orElse(null);
    }

    /** The shared catalog is changed only by Smart Farm staff, since every farm uses it; a farm's own entries only by that farm. */
    private void requireCanChange(Long ownerFarmId, String what, Long id) {
        if (ownerFarmId == null) {
            boolean farmAccount = tenancy.current().map(c -> c.companyRole() == null).orElse(false);
            if (farmAccount) {
                throw new ForbiddenException("The shared catalog is kept by Smart Farm, because every farm uses it. Add your own model for your farm instead.");
            }
        } else {
            tenancy.check(null, ownerFarmId, what, id);
        }
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
