package com.smartfarm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after too many wrong passwords for one username,
 * that username is locked for a while. A correct login clears the counter.
 * Trade-off: someone who knows a username can lock it out temporarily; the lock is
 * short, and the owner can still reset passwords for other users.
 */
@Component
public class LoginGuard {

    private record Failures(int count, Instant firstAt) {
    }

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    public LoginGuard(Clock clock,
                      @Value("${farm.login.max-failures:5}") int maxFailures,
                      @Value("${farm.login.lock-time:PT15M}") Duration window) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** Throws when the username is locked; tells the person how long to wait. */
    public void check(String username) {
        Failures f = current(username);
        if (f != null && f.count() >= maxFailures) {
            long minutes = Math.max(1, Duration.between(clock.instant(), f.firstAt().plus(window)).toMinutes() + 1);
            throw new TooManyAttemptsException("Too many wrong passwords. Wait " + minutes + " min and try again, or ask the farm owner to reset your password.");
        }
    }

    public void failed(String username) {
        Instant now = clock.instant();
        failures.compute(username, (k, f) -> f == null || expired(f, now) ? new Failures(1, now) : new Failures(f.count() + 1, f.firstAt()));
    }

    public void succeeded(String username) {
        failures.remove(username);
    }

    private Failures current(String username) {
        Failures f = failures.get(username);
        if (f != null && expired(f, clock.instant())) {
            failures.remove(username);
            return null;
        }
        return f;
    }

    private boolean expired(Failures f, Instant now) {
        return now.isAfter(f.firstAt().plus(window));
    }
}
