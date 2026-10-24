package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** A mist sprayer that raises the air humidity of a greenhouse or a nursery. */
@Entity
@DiscriminatorValue("MISTER")
public class Mister extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 5;

    protected Mister() {
    }

    Mister(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.MISTER; }
    @Override public String getActionVerb() { return "Misting"; }
}
