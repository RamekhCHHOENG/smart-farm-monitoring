package com.smartfarm.service;

import com.smartfarm.domain.*;
import com.smartfarm.repository.AlertRepository;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.FarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The Smart Farm company's own work: making a farm for a customer and inviting its owner, watching every farm's
 * health, suspending a farm, and managing company staff and which farms support staff look after.
 * Admins do all of it; support staff only see the farms assigned to them.
 */
@Service
@Transactional
public class CompanyService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,30}");

    /** One farm as the company sees it: who owns it and whether it is healthy. */
    public record FarmSummary(Long id, String name, String location, String status, boolean supportAllowed, Instant createdAt,
                              String ownerContact, List<String> owners, int zones, int sensors, int sensorsOnline,
                              int actuatorsRunning, long openAlerts, Instant lastReadingAt, List<AppUser> support) {
    }

    public record CreatedFarm(FarmSummary farm, String ownerUsername, String inviteToken) {
    }

    public record CreatedStaff(AppUser staff, String inviteToken) {
    }

    private final FarmRepository farms;
    private final AppUserRepository users;
    private final AlertRepository alerts;
    private final AccessPolicy access;
    private final InviteService invites;
    private final AuthService auth;
    private final Clock clock;

    public CompanyService(FarmRepository farms, AppUserRepository users, AlertRepository alerts, AccessPolicy access,
                          InviteService invites, AuthService auth, Clock clock) {
        this.farms = farms;
        this.users = users;
        this.alerts = alerts;
        this.access = access;
        this.invites = invites;
        this.auth = auth;
        this.clock = clock;
    }

    // ----- farms -----

    @Transactional(readOnly = true)
    public List<FarmSummary> farms(Long userId) {
        AppUser me = access.requireCompany(userId, false);
        return farms.findAllByOrderByNameAsc().stream()
                .filter(f -> me.getCompanyRole().equals("ADMIN") || f.getSupport().stream().anyMatch(s -> s.getId().equals(userId)))
                .map(this::summary)
                .toList();
    }

    /** Makes an empty farm and its owner's account; the owner sets a password through the returned invite link. */
    public CreatedFarm createFarm(Long userId, String name, String location, String ownerName, String ownerUsername, String ownerContact) {
        access.requireCompany(userId, true);
        String username = checkNewUsername(ownerUsername);
        Farm farm = farms.save(Farm.createdByCompany(name.trim(), location == null ? "" : location.trim(),
                ownerContact == null ? null : ownerContact.trim(), clock.instant()));
        AppUser owner = users.save(new AppUser(farm, username, ownerName.trim(), invites.unusablePasswordHash(), Role.OWNER));
        return new CreatedFarm(summary(farm), username, invites.create(owner));
    }

    public FarmSummary updateFarm(Long userId, Long farmId, String name, String location, String ownerContact) {
        access.requireCompany(userId, true);
        Farm farm = farm(farmId);
        farm.update(name.trim(), location == null ? "" : location.trim());
        farm.setOwnerContact(ownerContact == null ? null : ownerContact.trim());
        return summary(farm);
    }

    /** A new link for the farm's owner (lost or expired); older links stop working. */
    public CreatedFarm ownerInvite(Long userId, Long farmId) {
        access.requireCompany(userId, true);
        Farm farm = farm(farmId);
        AppUser owner = users.findByFarm_IdOrderByIdAsc(farmId).stream().filter(u -> u.getRole() == Role.OWNER).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(farm.getName() + " has no owner account"));
        return new CreatedFarm(summary(farm), owner.getUsername(), invites.create(owner));
    }

    /** A suspended farm keeps its data but its people cannot use it (for example, unpaid); reactivating restores it. */
    public FarmSummary setStatus(Long userId, Long farmId, String status) {
        access.requireCompany(userId, true);
        Farm farm = farm(farmId);
        farm.setStatus(status);
        if (farm.isSuspended()) {
            users.findByFarm_IdOrderByIdAsc(farmId).forEach(u -> auth.logoutEverywhere(u.getId()));
        }
        return summary(farm);
    }

    /** Which company support staff look after the farm (they still need the owner's consent to open it). */
    public FarmSummary assignSupport(Long userId, Long farmId, List<Long> staffIds) {
        access.requireCompany(userId, true);
        Farm farm = farm(farmId);
        Set<AppUser> staff = new LinkedHashSet<>();
        for (Long id : staffIds) {
            AppUser s = users.findById(id).orElseThrow(() -> new NotFoundException("Staff", id));
            if (!"SUPPORT".equals(s.getCompanyRole())) {
                throw new IllegalArgumentException(s.getName() + " is not company support staff");
            }
            staff.add(s);
        }
        farm.assignSupport(staff);
        return summary(farm);
    }

    // ----- company staff -----

    @Transactional(readOnly = true)
    public List<AppUser> staff(Long userId) {
        access.requireCompany(userId, true);
        return users.findByCompanyRoleIsNotNullOrderByNameAsc();
    }

    public CreatedStaff createStaff(Long userId, String username, String name, String companyRole) {
        access.requireCompany(userId, true);
        String normalized = checkNewUsername(username);
        AppUser staff = users.save(AppUser.company(normalized, name.trim(), invites.unusablePasswordHash(), companyRole));
        return new CreatedStaff(staff, invites.create(staff));
    }

    public CreatedStaff staffInvite(Long userId, Long staffId) {
        access.requireCompany(userId, true);
        AppUser staff = companyAccount(staffId);
        return new CreatedStaff(staff, invites.create(staff));
    }

    public void removeStaff(Long userId, Long staffId) {
        access.requireCompany(userId, true);
        if (userId.equals(staffId)) {
            throw new IllegalArgumentException("You cannot remove your own account");
        }
        AppUser staff = companyAccount(staffId);
        if ("ADMIN".equals(staff.getCompanyRole())
                && users.findByCompanyRoleIsNotNullOrderByNameAsc().stream().filter(u -> "ADMIN".equals(u.getCompanyRole())).count() <= 1) {
            throw new IllegalArgumentException("The company needs at least one admin");
        }
        farms.findAll().forEach(f -> f.getSupport().removeIf(s -> s.getId().equals(staffId)));
        users.delete(staff);
        auth.logoutEverywhere(staffId);
    }

    // ----- helpers -----

    private FarmSummary summary(Farm farm) {
        List<Sensor> sensors = farm.getZones().stream().flatMap(z -> z.getSensors().stream()).toList();
        List<Actuator> actuators = farm.getZones().stream().flatMap(z -> z.getActuators().stream()).toList();
        long open = alerts.findByFarmIdAndStatusOrderByRaisedAtDesc(farm.getId(), AlertStatus.OPEN).stream().filter(a -> !a.isLab()).count();
        Instant last = sensors.stream().map(Sensor::getLastSeenAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        List<String> owners = users.findByFarm_IdOrderByIdAsc(farm.getId()).stream()
                .filter(u -> u.getRole() == Role.OWNER).map(AppUser::getName).toList();
        return new FarmSummary(farm.getId(), farm.getName(), farm.getLocation(), farm.getStatus(), farm.isSupportAllowed(),
                farm.getCreatedAt(), farm.getOwnerContact(), owners, farm.getZones().size(), sensors.size(),
                (int) sensors.stream().filter(s -> s.getStatus() == SensorStatus.ONLINE).count(),
                (int) actuators.stream().filter(a -> a.getStatus() == ActuatorStatus.RUNNING).count(),
                open, last, List.copyOf(farm.getSupport()));
    }

    private Farm farm(Long farmId) {
        return farms.findById(farmId).orElseThrow(() -> new NotFoundException("Farm", farmId));
    }

    private AppUser companyAccount(Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> new NotFoundException("Staff", id));
        if (!u.isCompany()) {
            throw new NotFoundException("Staff", id);
        }
        return u;
    }

    private String checkNewUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Username: use 3-30 letters, numbers, dots or dashes, no spaces");
        }
        if (users.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("Username '" + normalized + "' is already taken");
        }
        return normalized;
    }
}
