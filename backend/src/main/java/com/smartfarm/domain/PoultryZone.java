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

    /** Hot chicken house starts the fan; low feed starts the feeder. */
    @Override
    public List<AutomationRule> createDefaultRules() {
        List<AutomationRule> rules = new ArrayList<>();
        findSensor(SensorType.TEMPERATURE).ifPresent(temp ->
                rules.add(new AutomationRule("Hot house - cool " + getName(), temp, Condition.ABOVE, 32,
                        findActuator(ActuatorType.FAN).orElse(null), 5, Severity.WARNING)));
        findSensor(SensorType.FEED_LEVEL).ifPresent(feed ->
                rules.add(new AutomationRule("Low feed - refill " + getName(), feed, Condition.BELOW, 20,
                        findActuator(ActuatorType.FEEDER).orElse(null), 2, Severity.WARNING)));
        return rules;
    }

    @Override
    public String getZoneType() { return "POULTRY"; }

    @Override
    public String getDetail() { return birdCount + " birds"; }

    public int getBirdCount() { return birdCount; }

    public void setBirdCount(int birdCount) { this.birdCount = birdCount; }
}
