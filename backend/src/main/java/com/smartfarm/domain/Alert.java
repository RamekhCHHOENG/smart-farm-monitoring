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
    private boolean delivered;

    private String acknowledgedBy;
    private Instant acknowledgedAt;

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

    public void markDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public Long getId() { return id; }
    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public Instant getRaisedAt() { return raisedAt; }
    public Long getZoneId() { return zoneId; }
    public Long getRuleId() { return ruleId; }
    public boolean isDelivered() { return delivered; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
