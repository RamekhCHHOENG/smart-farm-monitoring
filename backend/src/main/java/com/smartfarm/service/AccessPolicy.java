package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.Role;
import com.smartfarm.repository.AppUserRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Enforces the permissions table from Deliverable 2. The Farm Owner has full access
 * to the farm; other roles only get the actions listed for them.
 */
@Component
public class AccessPolicy {

    private final AppUserRepository users;

    public AccessPolicy(AppUserRepository users) {
        this.users = users;
    }

    public AppUser require(Long userId, Role... allowed) {
        if (userId == null) {
            throw new UnauthorizedException("Please log in");
        }
        AppUser user = users.findById(userId).orElseThrow(() -> new ForbiddenException("Unknown user " + userId));
        if (user.getRole() == Role.OWNER || Arrays.asList(allowed).contains(user.getRole())) {
            return user;
        }
        throw new ForbiddenException(switch (user.getRole()) {
            case WORKER -> "A Farm Worker account cannot do this. Ask the Farm Owner.";
            case TECHNICIAN -> "A Technician account cannot do this. Ask the Farm Owner.";
            case OWNER -> "Not allowed";
        });
    }
}
