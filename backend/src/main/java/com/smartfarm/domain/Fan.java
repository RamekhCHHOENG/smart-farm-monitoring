package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("FAN")
public class Fan extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 30;

    protected Fan() {
    }

    Fan(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.FAN; }
    @Override public String getActionVerb() { return "Cooling"; }
    @Override public boolean opposes(ActuatorType other) { return other == ActuatorType.HEATER; }
    @Override public String getNoEffectHint() { return "the fan motor and the air inlet"; }
    @Override public java.util.List<ModelEffect> getEffects() { return java.util.List.of(new ModelEffect("TEMPERATURE", -1), new ModelEffect("HUMIDITY", -1)); }
}
