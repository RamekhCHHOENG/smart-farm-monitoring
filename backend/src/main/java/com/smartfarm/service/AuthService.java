package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.UserSession;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.UserSessionRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Issues opaque bearer tokens after a password check. Sessions are stored in the database (only the
 * token's SHA-256), so people stay signed in when the server restarts.
 */
@Service
public class AuthService {

    public record LoginResult(String token, Instant expiresAt, AppUser user) {
    }

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    private final UserSessionRepository sessions;
    private final LoginGuard guard;

    public AuthService(AppUserRepository users, UserSessionRepository sessions, PasswordEncoder encoder, Clock clock,
                       LoginGuard guard, @Value("${farm.session-ttl:PT12H}") Duration ttl) {
        this.users = users;
        this.sessions = sessions;
        this.guard = guard;
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public LoginResult login(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        guard.check(normalized);
        Optional<AppUser> found = users.findByUsername(normalized)
                .filter(u -> encoder.matches(password, u.getPasswordHash()));
        if (found.isEmpty()) {
            guard.failed(normalized);
            // Same message for unknown user and wrong password, so usernames cannot be discovered.
            throw new UnauthorizedException("Wrong username or password");
        }
        guard.succeeded(normalized);
        return startSession(found.get());
    }

    /** Signs the person in: a new random token that is valid until the session time runs out. */
    @Transactional
    public LoginResult startSession(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        sessions.save(new UserSession(user, hash(token), now, expiresAt));
        return new LoginResult(token, expiresAt, user);
    }

    /** Every user changes their own password; the current password is required. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Please log in"));
        if (!encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is wrong");
        }
        checkNewPassword(newPassword);
        user.changePasswordHash(encoder.encode(newPassword));
    }

    static void checkNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("New password must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    /** After a password reset or removal, every open session of that user ends. */
    @Transactional
    public void logoutEverywhere(Long userId) {
        sessions.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findByTokenHash(hash(token))
                .filter(s -> s.validAt(clock.instant()))
                .map(UserSession::getUserId);
    }

    @Transactional
    public void logout(String token) {
        if (token != null) {
            sessions.deleteByTokenHash(hash(token));
        }
    }

    /** Expired sessions are only rows nobody can use; clear them out now and then. */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    @Transactional
    public int deleteExpiredSessions() {
        return sessions.deleteExpired(clock.instant());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
