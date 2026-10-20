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

    /** Dry soil starts the pump; extreme heat only raises an alert (no actuator). */
    @Override
    public List<AutomationRule> createDefaultRules() {
        List<AutomationRule> rules = new ArrayList<>();
        findSensor(SensorType.SOIL_MOISTURE).ifPresent(soil ->
                rules.add(new AutomationRule("Dry soil - water " + getName(), soil, Condition.BELOW, 30,
                        findActuator(ActuatorType.WATER_PUMP).orElse(null), 3, Severity.WARNING)));
        findSensor(SensorType.TEMPERATURE).ifPresent(temp ->
                rules.add(new AutomationRule("Heat stress - " + getName(), temp, Condition.ABOVE, 38,
                        null, 0, Severity.CRITICAL)));
        return rules;
    }

    @Override
    public String getZoneType() { return "CROP"; }

    @Override
    public String getDetail() { return cropName; }

    public String getCropName() { return cropName; }

    public void setCropName(String cropName) { this.cropName = cropName; }
}
