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
    private final Tenancy tenancy;

    public AccessPolicy(AppUserRepository users, Tenancy tenancy) {
        this.users = users;
        this.tenancy = tenancy;
    }

    public AppUser require(Long userId, Role... allowed) {
        if (userId == null) {
            throw new UnauthorizedException("Please log in");
        }
        AppUser user = users.findById(userId).orElseThrow(() -> new ForbiddenException("Unknown user " + userId));
        // the role on the farm this request works on: a company account's role comes from the farm it chose
        Role role = tenancy.current().map(Tenancy.Context::role).orElse(user.getRole());
        if (role == null) {
            throw new ForbiddenException("Choose a farm first");
        }
        if (role == Role.OWNER || Arrays.asList(allowed).contains(role)) {
            return user;
        }
        throw new ForbiddenException(switch (role) {
            case WORKER -> "A Farm Worker account cannot do this. Ask the Farm Owner.";
            case TECHNICIAN -> user.isCompany() ? "Company support cannot do this on a farm. Ask the Farm Owner."
                    : "A Technician account cannot do this. Ask the Farm Owner.";
            case OWNER -> "Not allowed";
        });
    }

    /** Company pages: a company account; `adminOnly` for creating farms, staff and assignments. */
    public AppUser requireCompany(Long userId, boolean adminOnly) {
        if (userId == null) {
            throw new UnauthorizedException("Please log in");
        }
        AppUser user = users.findById(userId).orElseThrow(() -> new ForbiddenException("Unknown user " + userId));
        if (!user.isCompany()) {
            throw new ForbiddenException("Only Smart Farm company staff can do this");
        }
        if (adminOnly && !user.getCompanyRole().equals("ADMIN")) {
            throw new ForbiddenException("Only a company admin can do this");
        }
        return user;
    }
}
