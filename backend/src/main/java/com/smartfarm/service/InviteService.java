package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.Invite;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.InviteRepository;
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
import java.util.Base64;
import java.util.HexFormat;

/**
 * One-time links for setting a first password: a new farm owner the company created, or new company staff.
 * Nobody ever shares a starting password. A new link for the same person cancels the older ones.
 */
@Service
@Transactional
public class InviteService {

    /** Who the link is for, shown on the "set your password" page. */
    public record InviteInfo(String name, String username, String farmName, String companyRole) {
    }

    private final InviteRepository invites;
    private final AppUserRepository users;
    private final AuthService auth;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites, AppUserRepository users, AuthService auth, PasswordEncoder encoder, Clock clock,
                         @Value("${farm.invite-ttl:P7D}") Duration ttl) {
        this.invites = invites;
        this.users = users;
        this.auth = auth;
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** Makes a new link token for the person (older unused links stop working) and returns it, shown once. */
    public String create(AppUser user) {
        invites.findByUser_IdAndUsedAtIsNull(user.getId()).forEach(old -> old.use(clock.instant()));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        invites.save(new Invite(user, hash(token), clock.instant(), clock.instant().plus(ttl)));
        return token;
    }

    /** A password nobody knows, for an account that will set its own through an invite. */
    public String unusablePasswordHash() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return encoder.encode(HexFormat.of().formatHex(bytes));
    }

    @Transactional(readOnly = true)
    public InviteInfo read(String token) {
        AppUser user = usable(token).getUser();
        return new InviteInfo(user.getName(), user.getUsername(), user.getFarm() == null ? null : user.getFarm().getName(),
                user.getCompanyRole());
    }

    /** Sets the person's password from the link, uses the link up, and signs them in. */
    public AuthService.LoginResult accept(String token, String password) {
        Invite invite = usable(token);
        AuthService.checkNewPassword(password);
        AppUser user = users.findById(invite.getUser().getId()).orElseThrow(() -> new NotFoundException("User", invite.getUser().getId()));
        user.changePasswordHash(encoder.encode(password));
        invite.use(clock.instant());
        auth.logoutEverywhere(user.getId());
        return auth.startSession(user);
    }

    private Invite usable(String token) {
        Invite invite = token == null ? null : invites.findByTokenHash(hash(token)).orElse(null);
        if (invite == null || !invite.usableAt(clock.instant())) {
            throw new NotFoundException("This invite link has expired or was already used. Ask Smart Farm for a new one.");
        }
        return invite;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
