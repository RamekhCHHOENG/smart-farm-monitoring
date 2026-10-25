package com.smartfarm.web;

import com.smartfarm.domain.AppUser;
import com.smartfarm.service.CompanyService;
import com.smartfarm.service.CompanyService.CreatedFarm;
import com.smartfarm.service.CompanyService.CreatedStaff;
import com.smartfarm.service.CompanyService.FarmSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** The Smart Farm company's pages: its customers' farms and its own staff. */
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    public record StaffDto(Long id, String username, String name, String companyRole) {
        static StaffDto of(AppUser u) {
            return new StaffDto(u.getId(), u.getUsername(), u.getName(), u.getCompanyRole());
        }
    }

    public record FarmSummaryDto(Long id, String name, String location, String status, boolean supportAllowed, Instant createdAt,
                                 String ownerContact, List<String> owners, int zones, int sensors, int sensorsOnline,
                                 int actuatorsRunning, long openAlerts, Instant lastReadingAt, List<StaffDto> support) {
        static FarmSummaryDto of(FarmSummary f) {
            return new FarmSummaryDto(f.id(), f.name(), f.location(), f.status(), f.supportAllowed(), f.createdAt(), f.ownerContact(),
                    f.owners(), f.zones(), f.sensors(), f.sensorsOnline(), f.actuatorsRunning(), f.openAlerts(), f.lastReadingAt(),
                    f.support().stream().map(StaffDto::of).toList());
        }
    }

    /** The invite token is shown once; the page turns it into a link to send to the person. */
    public record InviteDto(FarmSummaryDto farm, StaffDto staff, String username, String inviteToken) {
        static InviteDto of(CreatedFarm c) {
            return new InviteDto(FarmSummaryDto.of(c.farm()), null, c.ownerUsername(), c.inviteToken());
        }

        static InviteDto of(CreatedStaff c) {
            return new InviteDto(null, StaffDto.of(c.staff()), c.staff().getUsername(), c.inviteToken());
        }
    }

    public record NewFarmRequest(@NotBlank @Size(max = 80) String name, @Size(max = 120) String location,
                                 @NotBlank @Size(max = 80) String ownerName, @NotBlank String ownerUsername,
                                 @Size(max = 80) String ownerContact) {
    }

    public record FarmUpdateRequest(@NotBlank @Size(max = 80) String name, @Size(max = 120) String location,
                                    @Size(max = 80) String ownerContact) {
    }

    public record StatusRequest(@NotNull @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {
    }

    public record SupportRequest(@NotNull List<Long> staffIds) {
    }

    public record NewStaffRequest(@NotBlank String username, @NotBlank @Size(max = 80) String name,
                                  @NotNull @Pattern(regexp = "ADMIN|SUPPORT") String companyRole) {
    }

    private final CompanyService company;

    public CompanyController(CompanyService company) {
        this.company = company;
    }

    @GetMapping("/farms")
    public List<FarmSummaryDto> farms(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return company.farms(userId).stream().map(FarmSummaryDto::of).toList();
    }

    @PostMapping("/farms")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteDto createFarm(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody NewFarmRequest req) {
        return InviteDto.of(company.createFarm(userId, req.name(), req.location(), req.ownerName(), req.ownerUsername(), req.ownerContact()));
    }

    @PutMapping("/farms/{id}")
    public FarmSummaryDto updateFarm(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                     @Valid @RequestBody FarmUpdateRequest req) {
        return FarmSummaryDto.of(company.updateFarm(userId, id, req.name(), req.location(), req.ownerContact()));
    }

    @PostMapping("/farms/{id}/invite")
    public InviteDto ownerInvite(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return InviteDto.of(company.ownerInvite(userId, id));
    }

    @PutMapping("/farms/{id}/status")
    public FarmSummaryDto status(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                 @Valid @RequestBody StatusRequest req) {
        return FarmSummaryDto.of(company.setStatus(userId, id, req.status()));
    }

    @PutMapping("/farms/{id}/support")
    public FarmSummaryDto support(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                                  @Valid @RequestBody SupportRequest req) {
        return FarmSummaryDto.of(company.assignSupport(userId, id, req.staffIds()));
    }

    @GetMapping("/staff")
    public List<StaffDto> staff(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return company.staff(userId).stream().map(StaffDto::of).toList();
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public InviteDto createStaff(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody NewStaffRequest req) {
        return InviteDto.of(company.createStaff(userId, req.username(), req.name(), req.companyRole()));
    }

    @PostMapping("/staff/{id}/invite")
    public InviteDto staffInvite(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        return InviteDto.of(company.staffInvite(userId, id));
    }

    @DeleteMapping("/staff/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStaff(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        company.removeStaff(userId, id);
    }
}
