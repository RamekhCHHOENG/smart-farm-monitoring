package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("WATER_PUMP")
public class WaterPump extends Actuator {

    static final int DEFAULT_MAX_MINUTES = 10;

    protected WaterPump() {
    }

    WaterPump(Zone zone, String name, int maxRunMinutes) {
        super(zone, name, maxRunMinutes);
    }

    @Override public ActuatorType getType() { return ActuatorType.WATER_PUMP; }
    @Override public String getActionVerb() { return "Watering"; }
}
