package com.smartfarm.domain;

/**
 * Lifecycle states of an actuator (Deliverable 9 - State Machine Diagram).
 * DECOMMISSIONED is the final state; the record is kept so device logs stay readable.
 */
public enum ActuatorStatus {
    IDLE, RUNNING, FAULT, OFFLINE, MAINTENANCE, DECOMMISSIONED
}
