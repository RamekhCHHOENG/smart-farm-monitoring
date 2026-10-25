package com.smartfarm.service;

import com.smartfarm.domain.*;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Which farm the current request works on, so one farm never sees another's data.
 *
 * The sign-in check (AuthInterceptor) sets it at the start of each request from a person's account: a farm account
 * always works on its own farm; a company account works on the farm it chose, if it may. Background jobs and
 * ESP32 boards have no person behind them, so for them nothing is set and nothing is limited (a board's key
 * already ties it to its devices).
 */
@Component
public class Tenancy {

    /** userId, the farm being worked on (null for a company account on company pages), and the role it has there. */
    public record Context(Long userId, Long farmId, Role role, String companyRole) {
    }

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static void set(Context context) { CURRENT.set(context); }
    public static void clear() { CURRENT.remove(); }

    public Optional<Context> current() { return Optional.ofNullable(CURRENT.get()); }

    /** True while a signed-in person works on a farm: lookups and lists are limited to that farm. */
    public boolean limited() {
        Context c = CURRENT.get();
        return c != null && c.farmId() != null;
    }

    /** The farm of this request; a company account must have chosen one. */
    public Long farmId() {
        Context c = CURRENT.get();
        if (c == null || c.farmId() == null) {
            throw new ForbiddenException("Choose a farm first");
        }
        return c.farmId();
    }

    /** Something from another farm is answered as "not found", so ids of other farms reveal nothing. */
    public <T> T check(T found, Long ownerFarmId, String what, Long id) {
        if (limited() && !Objects.equals(CURRENT.get().farmId(), ownerFarmId)) {
            throw new NotFoundException(what, id);
        }
        return found;
    }

    public Zone zone(Zone z) { return check(z, z.getFarm().getId(), "Zone", z.getId()); }
    public Sensor sensor(Sensor s) { return check(s, s.getZone().getFarm().getId(), "Sensor", s.getId()); }
    public Actuator actuator(Actuator a) { return check(a, a.getZone().getFarm().getId(), "Actuator", a.getId()); }
    public AutomationRule rule(AutomationRule r) { return check(r, r.getSensor().getZone().getFarm().getId(), "Rule", r.getId()); }
    public ScheduledTask task(ScheduledTask t) { return check(t, t.getActuator().getZone().getFarm().getId(), "Task", t.getId()); }
    public Alert alert(Alert a) { return check(a, a.getFarmId(), "Alert", a.getId()); }
    public FarmDevice device(FarmDevice d) { return check(d, d.getZone().getFarm().getId(), "Device", d.getId()); }
    public ControllerBoard board(ControllerBoard b) { return check(b, b.getFarm() == null ? null : b.getFarm().getId(), "Controller board", b.getId()); }
    public AppUser user(AppUser u) { return check(u, u.getFarm() == null ? null : u.getFarm().getId(), "User", u.getId()); }
}
