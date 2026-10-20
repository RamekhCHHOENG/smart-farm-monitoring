package com.smartfarm.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Audit entry for every actuator action (FR-08, FR-10). Kept as history, so no foreign keys. */
@Entity
@Table(indexes = @Index(columnList = "zoneId, at"))
public class DeviceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long zoneId;
    private Long actuatorId;
    private String actuatorName;
    private String action;

    @Enumerated(EnumType.STRING)
    private LogSource source;

    private String userName;

    @Column(length = 500)
    private String detail;

    private Instant at;

    protected DeviceLog() {
    }

    public DeviceLog(Actuator actuator, String action, LogSource source, String userName, String detail, Instant at) {
        this.zoneId = actuator.getZone().getId();
        this.actuatorId = actuator.getId();
        this.actuatorName = actuator.getName();
        this.action = action;
        this.source = source;
        this.userName = userName;
        this.detail = detail;
        this.at = at;
    }

    public Long getId() { return id; }
    public Long getZoneId() { return zoneId; }
    public Long getActuatorId() { return actuatorId; }
    public String getActuatorName() { return actuatorName; }
    public String getAction() { return action; }
    public LogSource getSource() { return source; }
    public String getUserName() { return userName; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
