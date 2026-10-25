package com.smartfarm.service;

import com.smartfarm.domain.AppUser;
import com.smartfarm.domain.Farm;
import com.smartfarm.domain.Role;
import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.repository.FarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out, for one request, which farm the signed-in person works on and with which role there.
 *
 * <ul>
 *   <li>Farm account: its own farm and its own role. A suspended farm can only sign in and read its account.</li>
 *   <li>Company ADMIN: any farm it chooses (header X-Farm-Id), as its owner: to set the farm up before handover.</li>
 *   <li>Company SUPPORT: a farm it is assigned to, as a technician, and only while the owner allows support.</li>
 * </ul>
 * A company account that has not chosen a farm works on company pages only.
 */
@Service
@Transactional(readOnly = true)
public class FarmAccessResolver {

    private final AppUserRepository users;
    private final FarmRepository farms;

    public FarmAccessResolver(AppUserRepository users, FarmRepository farms) {
        this.users = users;
        this.farms = farms;
    }

    public Tenancy.Context resolve(Long userId, String farmHeader, boolean accountOnly) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Please log in"));
        if (!user.isCompany()) {
            Farm farm = user.getFarm();
            if (farm.isSuspended() && !accountOnly) {
                throw new ForbiddenException("This farm is suspended. Please contact Smart Farm.");
            }
            return new Tenancy.Context(userId, farm.getId(), user.getRole(), null);
        }
        Long farmId = parse(farmHeader);
        if (farmId == null) {
            return new Tenancy.Context(userId, null, null, user.getCompanyRole());
        }
        Farm farm = farms.findById(farmId).orElseThrow(() -> new NotFoundException("Farm", farmId));
        if (user.getCompanyRole().equals("ADMIN")) {
            return new Tenancy.Context(userId, farmId, Role.OWNER, "ADMIN");
        }
        if (farm.getSupport().stream().noneMatch(s -> s.getId().equals(userId))) {
            throw new ForbiddenException("You are not assigned to " + farm.getName() + ". Ask a company admin.");
        }
        if (!farm.isSupportAllowed()) {
            throw new ForbiddenException("The owner of " + farm.getName() + " has turned off support access.");
        }
        return new Tenancy.Context(userId, farmId, Role.TECHNICIAN, "SUPPORT");
    }

    private static Long parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("X-Farm-Id must be a farm number");
        }
    }
}
