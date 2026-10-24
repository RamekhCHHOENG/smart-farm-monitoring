package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A grow light or house light: switched by a light-level rule, or on a schedule for long days. */
@Entity
@DiscriminatorValue("LIGHT")
public class Light extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 120;

    protected Light() {
    }

    Light(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.LIGHT; }
    @Override public String getActionVerb() { return "Lighting"; }
}
