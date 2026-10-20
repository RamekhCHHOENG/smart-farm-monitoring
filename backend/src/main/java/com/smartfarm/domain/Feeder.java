package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("FEEDER")
public class Feeder extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 5;

    protected Feeder() {
    }

    Feeder(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.FEEDER; }
    @Override public String getActionVerb() { return "Feeding"; }
}
