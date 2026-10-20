package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.notification.NotificationService;
import com.smartfarm.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Transactional
public class AlertService {

    private final AlertRepository alerts;
    private final NotificationService notifier;
    private final AccessPolicy access;
    private final Clock clock;

    public AlertService(AlertRepository alerts, NotificationService notifier, AccessPolicy access, Clock clock) {
        this.alerts = alerts;
        this.notifier = notifier;
        this.access = access;
        this.clock = clock;
    }

    /** Creates the alert, stores it, and delivers it through the configured channel (FR-09). */
    public Alert raise(String message, Severity severity, Long zoneId, Long ruleId) {
        Alert alert = alerts.save(new Alert(message, severity, zoneId, ruleId, clock.instant()));
        alert.markDelivered(notifier.send(alert));
        return alert;
    }

    public Alert acknowledge(Long userId, Long alertId) {
        AppUser user = access.require(userId, Role.WORKER, Role.TECHNICIAN);
        Alert alert = alerts.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId));
        alert.acknowledge(user, clock.instant());
        return alert;
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status) {
        return status == null ? alerts.findTop200ByOrderByRaisedAtDesc() : alerts.findByStatusOrderByRaisedAtDesc(status);
    }
}
