package com.smartfarm.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Master data: a real product you can buy (DHT22, 36-inch exhaust fan, 12 V solenoid valve, ESP32 board...).
 * A farm device is created from a model, which says what it measures (its channels) or what it does
 * (actuator kind and effects), how it is wired, and how it looks. Owners and technicians add their own.
 * Category and kind are plain text, not database enums, so new values never need a schema change.
 */
@Entity
@Table(name = "device_model")
public class DeviceModel {

    public static final Set<String> CATEGORIES = Set.of("SENSOR", "ACTUATOR", "CONTROLLER");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    private String manufacturer;

    /** SENSOR, ACTUATOR or CONTROLLER */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(length = 1000)
    private String description;

    /** analog, digital, 1-wire, I2C, UART, RS485, relay, pulse... */
    private String interfaceType;
    private String voltage;
    private Double priceUsd;
    @Column(length = 500)
    private String datasheetUrl;
    @Column(length = 1000)
    private String wiringNotes;

    /** Name of an open-source Wokwi part drawing (dht22, hc-sr04, servo...), or null. */
    @Column(length = 60)
    private String wokwiPart;
    /** Which built-in drawing to use when there is no photo or Wokwi part (SOIL_MOISTURE, FAN...). */
    @Column(length = 40)
    private String artType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "device_model_channel", joinColumns = @JoinColumn(name = "model_id"))
    @OrderColumn(name = "position")
    private List<ModelChannel> channels = new ArrayList<>();

    /** For an actuator: a built-in kind (WATER_PUMP, FAN...) or null for a kind of your own. */
    @Column(length = 40)
    private String actuatorType;
    /** For an actuator of your own: its verb in logs and alerts ("Ventilating"). */
    private String actionVerb;
    private Integer defaultMaxRunMinutes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "device_model_effect", joinColumns = @JoinColumn(name = "model_id"))
    @OrderColumn(name = "position")
    private List<ModelEffect> effects = new ArrayList<>();

    private boolean hasPicture;
    private boolean builtIn;
    private boolean retired;

    /** null = the shared Smart Farm catalog (kept by company staff); a farm id = a model one farm added for itself. */
    private Long farmId;

    protected DeviceModel() {
    }

    public DeviceModel(String name, String category, boolean builtIn) {
        this.name = name;
        this.category = checkCategory(category);
        this.builtIn = builtIn;
    }

    private static String checkCategory(String category) {
        if (category == null || !CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Category must be SENSOR, ACTUATOR or CONTROLLER");
        }
        return category;
    }

    /** Fluent setters keep the seed list readable; each one checks its own value. */
    public DeviceModel describe(String manufacturer, String description, String interfaceType, String voltage, Double priceUsd) {
        this.manufacturer = manufacturer;
        this.description = description;
        this.interfaceType = interfaceType;
        this.voltage = voltage;
        if (priceUsd != null && priceUsd < 0) {
            throw new IllegalArgumentException("The price cannot be negative");
        }
        this.priceUsd = priceUsd;
        return this;
    }

    public DeviceModel looks(String wokwiPart, String artType) {
        this.wokwiPart = blankToNull(wokwiPart);
        this.artType = blankToNull(artType);
        return this;
    }

    public DeviceModel docs(String datasheetUrl, String wiringNotes) {
        this.datasheetUrl = blankToNull(datasheetUrl);
        this.wiringNotes = blankToNull(wiringNotes);
        return this;
    }

    public DeviceModel measures(List<ModelChannel> channels) {
        if (!category.equals("SENSOR") && !channels.isEmpty()) {
            throw new IllegalArgumentException("Only a sensor model has readings");
        }
        this.channels.clear();
        this.channels.addAll(channels);
        return this;
    }

    /** {@code actuatorType} is a built-in kind name, or null for a kind of your own (then {@code actionVerb} names it). */
    public DeviceModel acts(String actuatorType, String actionVerb, Integer defaultMaxRunMinutes, List<ModelEffect> effects) {
        if (!category.equals("ACTUATOR")) {
            throw new IllegalArgumentException("Only an actuator model runs");
        }
        if (actuatorType != null) {
            ActuatorType.valueOf(actuatorType); // refuses an unknown built-in kind
        }
        if (defaultMaxRunMinutes != null && (defaultMaxRunMinutes <= 0 || defaultMaxRunMinutes > 240)) {
            throw new IllegalArgumentException("The safety limit must be 1-240 minutes");
        }
        this.actuatorType = actuatorType;
        this.actionVerb = blankToNull(actionVerb);
        this.defaultMaxRunMinutes = defaultMaxRunMinutes;
        this.effects.clear();
        this.effects.addAll(effects);
        return this;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void markPicture(boolean hasPicture) {
        this.hasPicture = hasPicture;
    }

    /** A retired model stays for the devices made from it, but is no longer offered for new ones. */
    public void setRetired(boolean retired) {
        this.retired = retired;
    }

    /** The built-in kind of a sensor channel's measurement, when the code matches one (null for CO2, pH...). */
    public static SensorType builtInSensorType(String measurementCode) {
        for (SensorType t : SensorType.values()) {
            if (t.name().equals(measurementCode)) {
                return t;
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getManufacturer() { return manufacturer; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getInterfaceType() { return interfaceType; }
    public String getVoltage() { return voltage; }
    public Double getPriceUsd() { return priceUsd; }
    public String getDatasheetUrl() { return datasheetUrl; }
    public String getWiringNotes() { return wiringNotes; }
    public String getWokwiPart() { return wokwiPart; }
    public String getArtType() { return artType; }
    public List<ModelChannel> getChannels() { return List.copyOf(channels); }
    public String getActuatorType() { return actuatorType; }
    public String getActionVerb() { return actionVerb; }
    public Integer getDefaultMaxRunMinutes() { return defaultMaxRunMinutes; }
    public List<ModelEffect> getEffects() { return List.copyOf(effects); }
    public boolean isHasPicture() { return hasPicture; }
    public boolean isBuiltIn() { return builtIn; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public boolean isRetired() { return retired; }
}
