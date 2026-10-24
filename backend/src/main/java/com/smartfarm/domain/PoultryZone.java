package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("POULTRY")
public class PoultryZone extends Zone {

    private int birdCount;

    protected PoultryZone() {
    }

    PoultryZone(Farm farm, String name, int birdCount) {
        super(farm, name);
        this.birdCount = birdCount;
    }

    /**
     * Hot chicken house starts the fan; low feed starts the feeder. Where the house has the matching
     * sensor and actuator, it also warms up when cold, ventilates when damp, and keeps its light and tank.
     */
    @Override
    public List<AutomationRule> createDefaultRules() {
        List<AutomationRule> rules = new ArrayList<>();
        // Fan and heater keep 21-29 °C between them: each stops well before the other one's start, so they
        // never take turns (and the interlock stops one if the other is started anyway).
        addRule(rules, "Hot house - cool " + getName(), SensorType.TEMPERATURE, Condition.ABOVE, 32,
                ActuatorType.FAN, 30, Severity.WARNING, 29.0);
        addRule(rules, "Low feed - refill " + getName(), SensorType.FEED_LEVEL, Condition.BELOW, 20,
                ActuatorType.FEEDER, 5, Severity.WARNING, 80.0);
        addActuatorRule(rules, "Cold house - heat " + getName(), SensorType.TEMPERATURE, Condition.BELOW, 18,
                ActuatorType.HEATER, 30, Severity.WARNING, 21.0);
        addActuatorRule(rules, "Damp house - ventilate " + getName(), SensorType.HUMIDITY, Condition.ABOVE, 85,
                ActuatorType.FAN, 30, Severity.WARNING, 75.0);
        addActuatorRule(rules, "Dim house - light " + getName(), SensorType.LIGHT_LEVEL, Condition.BELOW, 100,
                ActuatorType.LIGHT, 60, Severity.INFO, null);
        addActuatorRule(rules, "Low tank - refill " + getName(), SensorType.WATER_LEVEL, Condition.BELOW, 20,
                ActuatorType.WATER_VALVE, 15, Severity.WARNING, 90.0);
        return rules;
    }

    @Override
    public String getZoneType() { return "POULTRY"; }

    @Override
    public String getDetail() { return birdCount + " birds"; }

    public int getBirdCount() { return birdCount; }

    public void setBirdCount(int birdCount) { this.birdCount = birdCount; }
}
