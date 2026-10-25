package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * An alert is history: it keeps plain ids and names instead of foreign keys,
 * so it survives when the zone, rule or device is later removed.
 */
@Entity
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.OPEN;

    private Instant raisedAt;
    private Long zoneId;
    private Long ruleId;

    /** The farm the alert belongs to (from its zone); older alerts are filled in on start. */
    private Long farmId;
    private boolean delivered;

    /** How many times the same problem was reported while this alert was still open. */
    private Integer repeatCount = 1;
    private Instant lastRaisedAt;

    private String acknowledgedBy;
    private Instant acknowledgedAt;

    /** Raised by a Farm Lab test zone. Nullable column, so older alerts count as farm alerts. */
    private Boolean lab;

    protected Alert() {
    }

    public Alert(String message, Severity severity, Long zoneId, Long ruleId, Instant raisedAt) {
        this.message = message;
        this.severity = severity;
        this.zoneId = zoneId;
        this.ruleId = ruleId;
        this.raisedAt = raisedAt;
    }

    public void acknowledge(AppUser user, Instant now) {
        if (status == AlertStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Alert " + id + " is already acknowledged");
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedBy = user.getName();
        acknowledgedAt = now;
    }

    /** The same problem happened again before anyone acknowledged it. */
    public void repeat(Instant now) {
        repeatCount = getRepeatCount() + 1;
        lastRaisedAt = now;
    }

    public void markLab() {
        this.lab = true;
    }

    public boolean isLab() {
        return Boolean.TRUE.equals(lab);
    }

    public void markDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public Instant getRaisedAt() { return raisedAt; }
    public int getRepeatCount() { return repeatCount == null ? 1 : repeatCount; }
    public Instant getLastRaisedAt() { return lastRaisedAt != null ? lastRaisedAt : raisedAt; }
    public Long getZoneId() { return zoneId; }
    public Long getFarmId() { return farmId; }
    public void setFarmId(Long farmId) { this.farmId = farmId; }
    public Long getRuleId() { return ruleId; }
    public boolean isDelivered() { return delivered; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
