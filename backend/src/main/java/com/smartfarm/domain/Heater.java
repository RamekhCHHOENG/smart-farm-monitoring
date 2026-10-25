package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A heater or brooder lamp that warms a zone (chicks, cold nights). */
@Entity
@DiscriminatorValue("HEATER")
public class Heater extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 30;

    protected Heater() {
    }

    Heater(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.HEATER; }
    @Override public String getActionVerb() { return "Heating"; }
    @Override public boolean opposes(ActuatorType other) { return other == ActuatorType.FAN; }
    @Override public String getNoEffectHint() { return "the heater's power and its element"; }
    @Override public java.util.List<ModelEffect> getEffects() { return java.util.List.of(new ModelEffect("TEMPERATURE", 1)); }
}
