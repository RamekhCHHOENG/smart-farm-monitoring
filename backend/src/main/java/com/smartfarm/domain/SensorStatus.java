package com.smartfarm.domain;

/**
 * What staff see for a sensor. It is worked out from the sensor's own facts (is a signal
 * arriving? did a technician take it out of service?), so it is never stored twice.
 *
 * <pre>
 * ONLINE  ── silent for 2 min ──► OFFLINE ── a valid reading ──► ONLINE
 * ONLINE / OFFLINE ── reportFault ──► FAULT
 * FAULT / OFFLINE ── startRepair ──► MAINTENANCE
 * MAINTENANCE ── completeRepair [a valid reading arrived since the repair began] ──► ONLINE
 * </pre>
 */
public enum SensorStatus {
    ONLINE, OFFLINE, FAULT, MAINTENANCE
}
