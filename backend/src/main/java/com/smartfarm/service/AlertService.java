package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.notification.NotificationService;
import com.smartfarm.repository.AlertRepository;
import com.smartfarm.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class AlertService {

    /** Which alerts a list shows: the real farm's (default), the Farm Lab's, or both. */
    public enum Scope { FARM, LAB, ALL }

    private final AlertRepository alerts;
    private final ZoneRepository zones;
    private final NotificationService notifier;
    private final AccessPolicy access;
    private final Tenancy tenancy;
    private final Clock clock;

    public AlertService(AlertRepository alerts, ZoneRepository zones, NotificationService notifier, AccessPolicy access,
                        Tenancy tenancy, Clock clock) {
        this.tenancy = tenancy;
        this.alerts = alerts;
        this.zones = zones;
        this.notifier = notifier;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Creates the alert, stores it, and delivers it through the configured channel (FR-09).
     * If the same problem is already open, it is counted on that alert instead, so staff
     * are not flooded with copies and the phone is not notified again.
     */
    public Alert raise(String message, Severity severity, Long zoneId, Long ruleId) {
        // the alert belongs to its zone's farm (or the farm a person is working on, for the rare alert without a zone)
        Long farmId = zoneId == null ? null : zones.findById(zoneId).map(z -> z.getFarm().getId()).orElse(null);
        if (farmId == null && tenancy.limited()) {
            farmId = tenancy.farmId();
        }
        Optional<Alert> open = alerts.findFirstByFarmIdAndStatusAndMessage(farmId, AlertStatus.OPEN, message);
        if (open.isPresent()) {
            open.get().repeat(clock.instant());
            return open.get();
        }
        Alert created = new Alert(message, severity, zoneId, ruleId, clock.instant());
        created.setFarmId(farmId);
        Alert alert = alerts.save(created);
        if (zoneId != null && zones.findById(zoneId).map(Zone::isLab).orElse(false)) {
            alert.markLab(); // a test: kept in the Farm Lab, never sent to staff phones
            return alert;
        }
        alert.markDelivered(notifier.send(alert));
        return alert;
    }

    public Alert acknowledge(Long userId, Long alertId) {
        AppUser user = access.require(userId, Role.WORKER, Role.TECHNICIAN);
        Alert alert = tenancy.alert(alerts.findById(alertId).orElseThrow(() -> new NotFoundException("Alert", alertId)));
        alert.acknowledge(user, clock.instant());
        return alert;
    }

    /** After checking the farm, staff clear every open alert at once. Returns how many were acknowledged. */
    public int acknowledgeAll(Long userId, Scope scope) {
        AppUser user = access.require(userId, Role.WORKER, Role.TECHNICIAN);
        List<Alert> open = list(AlertStatus.OPEN, scope);
        open.forEach(a -> a.acknowledge(user, clock.instant()));
        return open.size();
    }

    @Transactional(readOnly = true)
    public List<Alert> list(AlertStatus status, Scope scope) {
        // a farm sees its own alerts; company staff on company pages see every farm's
        List<Alert> found;
        if (tenancy.limited()) {
            Long farmId = tenancy.farmId();
            found = status == null ? alerts.findTop200ByFarmIdOrderByRaisedAtDesc(farmId) : alerts.findByFarmIdAndStatusOrderByRaisedAtDesc(farmId, status);
        } else {
            found = status == null ? alerts.findTop200ByOrderByRaisedAtDesc() : alerts.findByStatusOrderByRaisedAtDesc(status);
        }
        return found.stream().filter(a -> scope == Scope.ALL || a.isLab() == (scope == Scope.LAB)).toList();
    }

    /** Open alerts of the real farm (the dashboard's count; Farm Lab tests are left out). */
    @Transactional(readOnly = true)
    public long countOpenOnFarm() {
        return list(AlertStatus.OPEN, Scope.FARM).size();
    }
}
