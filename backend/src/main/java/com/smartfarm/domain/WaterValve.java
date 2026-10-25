package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** The valve that fills a water tank, so the pumps never run dry. */
@Entity
@DiscriminatorValue("WATER_VALVE")
public class WaterValve extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 15;

    protected WaterValve() {
    }

    WaterValve(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.WATER_VALVE; }
    @Override public String getActionVerb() { return "Refilling"; }
    @Override public String getNoEffectHint() { return "the valve and the water supply"; }
    @Override public java.util.List<ModelEffect> getEffects() { return java.util.List.of(new ModelEffect("WATER_LEVEL", 1)); }
}
