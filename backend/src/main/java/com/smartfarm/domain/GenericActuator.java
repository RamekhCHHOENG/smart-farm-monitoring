package com.smartfarm.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.util.List;

/**
 * An actuator of a kind added in the catalog (a CO2 vent, a siren, a shade motor...). It follows the same
 * state machine and safety limit as every actuator; what it does to the farm comes from its model's effects.
 */
@Entity
@DiscriminatorValue("GENERIC")
public class GenericActuator extends Actuator {

    /** Copied from the model when installed, so renaming a catalog entry does not rewrite old alerts. */
    private String kindName;
    private String verb;

    protected GenericActuator() {
    }

    GenericActuator(Zone zone, String name, int maxRunMinutes, String kindName, String verb) {
        super(zone, name, maxRunMinutes);
        this.kindName = kindName;
        this.verb = verb;
    }

    @Override public ActuatorType getType() { return null; }
    @Override public String getKindCode() { return "GENERIC"; }
    @Override public String getKindName() { return kindName; }
    @Override public String getActionVerb() { return verb != null ? verb : "Running"; }

    @Override
    public List<ModelEffect> getEffects() {
        return getDevice() == null ? List.of() : getDevice().getModel().getEffects();
    }
}
