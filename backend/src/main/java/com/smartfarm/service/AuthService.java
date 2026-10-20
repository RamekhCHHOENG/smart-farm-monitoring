package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues opaque bearer tokens after a password check. Tokens live in memory, so a
 * server restart logs everyone out; that is acceptable for a single-farm server.
 */
@Service
public class AuthService {

    public record Session(Long userId, Instant expiresAt) {
    }

    public record LoginResult(String token, Instant expiresAt, AppUser user) {
    }

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(AppUserRepository users, PasswordEncoder encoder, Clock clock,
                       @Value("${farm.session-ttl:PT12H}") Duration ttl) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public LoginResult login(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByUsername(normalized)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                // Same message for unknown user and wrong password, so usernames cannot be discovered.
                .orElseThrow(() -> new UnauthorizedException("Wrong username or password"));
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(ttl);
        sessions.put(token, new Session(user.getId(), expiresAt));
        return new LoginResult(token, expiresAt, user);
    }

    /** Every user changes their own password; the current password is required. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Please log in"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is wrong");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        user.changePasswordHash(encoder.encode(newPassword));
    }

    public Optional<Long> resolve(String token) {
        Session session = token == null ? null : sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.userId());
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
