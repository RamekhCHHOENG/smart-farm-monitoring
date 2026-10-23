package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.Role;
import com.smartfarm.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The Farm Owner manages who can sign in: add staff, change their role, reset a password, remove them. */
@Service
@Transactional
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,30}");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AccessPolicy access;
    private final AuthService auth;
    private final LoginGuard guard;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AccessPolicy access, AuthService auth, LoginGuard guard) {
        this.guard = guard;
        this.users = users;
        this.encoder = encoder;
        this.access = access;
        this.auth = auth;
    }

    @Transactional(readOnly = true)
    public List<AppUser> list(Long userId) {
        access.require(userId);
        return users.findAllByOrderByIdAsc();
    }

    public AppUser create(Long userId, String username, String name, Role role, String password) {
        AppUser owner = access.require(userId);
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Username: use 3-30 letters, numbers, dots or dashes, no spaces");
        }
        if (users.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("Username '" + normalized + "' is already taken");
        }
        AuthService.checkNewPassword(password);
        return users.save(new AppUser(owner.getFarm(), normalized, name.trim(), encoder.encode(password), role));
    }

    public AppUser update(Long userId, Long targetId, String name, Role role) {
        access.require(userId);
        AppUser target = find(targetId);
        if (target.getRole() == Role.OWNER && role != Role.OWNER) {
            requireAnotherOwner(target);
        }
        target.update(name.trim(), role);
        return target;
    }

    /** For a worker who forgot their password or was locked out. Signs them out everywhere. */
    public void resetPassword(Long userId, Long targetId, String newPassword) {
        access.require(userId);
        AppUser target = find(targetId);
        AuthService.checkNewPassword(newPassword);
        target.changePasswordHash(encoder.encode(newPassword));
        auth.logoutEverywhere(targetId);
        guard.succeeded(target.getUsername()); // a locked-out user can sign in right away
    }

    /** Alerts and device logs keep the person's name as text, so history stays readable. */
    public void delete(Long userId, Long targetId) {
        access.require(userId);
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("You cannot remove your own account");
        }
        AppUser target = find(targetId);
        if (target.getRole() == Role.OWNER) {
            requireAnotherOwner(target);
        }
        users.delete(target);
        auth.logoutEverywhere(targetId);
    }

    private void requireAnotherOwner(AppUser target) {
        boolean another = users.findAllByOrderByIdAsc().stream()
                .anyMatch(u -> u.getRole() == Role.OWNER && !u.getId().equals(target.getId()));
        if (!another) {
            throw new IllegalArgumentException("The farm needs at least one Farm Owner");
        }
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
