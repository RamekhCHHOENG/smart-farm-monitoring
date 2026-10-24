package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("CROP")
public class CropZone extends Zone {

    private String cropName;

    protected CropZone() {
    }

    CropZone(Farm farm, String name, String cropName) {
        super(farm, name);
        this.cropName = cropName;
    }

    /**
     * Dry soil starts the pump; extreme heat only raises an alert (no actuator). Where the zone has
     * the matching sensor and actuator, it also gets the mister, the light and the tank valve.
     */
    @Override
    public List<AutomationRule> createDefaultRules() {
        List<AutomationRule> rules = new ArrayList<>();
        // Most rules keep a range: water until 45 %, mist until 55 %, fill the tank to 90 %. The minutes are the
        // longest run. The light stays timed: it brightens its own sensor, so "stop when bright" would flicker.
        addRule(rules, "Dry soil - water " + getName(), SensorType.SOIL_MOISTURE, Condition.BELOW, 30,
                ActuatorType.WATER_PUMP, 10, Severity.WARNING, 45.0);
        addRule(rules, "Heat stress - " + getName(), SensorType.TEMPERATURE, Condition.ABOVE, 38,
                null, 0, Severity.CRITICAL, null);
        addActuatorRule(rules, "Dry air - mist " + getName(), SensorType.HUMIDITY, Condition.BELOW, 40,
                ActuatorType.MISTER, 5, Severity.WARNING, 55.0);
        addActuatorRule(rules, "Dark - light " + getName(), SensorType.LIGHT_LEVEL, Condition.BELOW, 2000,
                ActuatorType.LIGHT, 60, Severity.INFO, null);
        addActuatorRule(rules, "Low tank - refill " + getName(), SensorType.WATER_LEVEL, Condition.BELOW, 20,
                ActuatorType.WATER_VALVE, 15, Severity.WARNING, 90.0);
        return rules;
    }

    @Override
    public String getZoneType() { return "CROP"; }

    @Override
    public String getDetail() { return cropName; }

    public String getCropName() { return cropName; }

    public void setCropName(String cropName) { this.cropName = cropName; }
}
