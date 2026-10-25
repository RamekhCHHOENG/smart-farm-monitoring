package com.smartfarm.web;

import com.smartfarm.repository.AppUserRepository;
import com.smartfarm.service.AuthService;
import com.smartfarm.service.AuthService.LoginResult;
import com.smartfarm.service.InviteService;
import com.smartfarm.service.NotFoundException;
import com.smartfarm.web.Dtos.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    public record AcceptInviteRequest(@NotBlank String password) {
    }

    private final AuthService authService;
    private final AppUserRepository users;
    private final InviteService invites;

    public AuthController(AuthService authService, AppUserRepository users, InviteService invites) {
        this.authService = authService;
        this.users = users;
        this.invites = invites;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        LoginResult result = authService.login(req.username(), req.password());
        return new LoginResponse(result.token(), result.expiresAt(), UserDto.of(result.user()));
    }

    /** The "set your password" page: who the invite link is for (no sign-in needed). */
    @GetMapping("/invites/{token}")
    public InviteService.InviteInfo invite(@PathVariable String token) {
        return invites.read(token);
    }

    /** Sets the password from the invite link and signs the person in. */
    @PostMapping("/invites/{token}")
    public LoginResponse acceptInvite(@PathVariable String token, @Valid @RequestBody AcceptInviteRequest req) {
        LoginResult result = invites.accept(token, req.password());
        return new LoginResponse(result.token(), result.expiresAt(), UserDto.of(result.user()));
    }

    @GetMapping("/me")
    public UserDto me(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return users.findById(userId).map(UserDto::of).orElseThrow(() -> new NotFoundException("User", userId));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
                               @Valid @RequestBody PasswordRequest req) {
        authService.changePassword(userId, req.currentPassword(), req.newPassword());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.bearerToken(request));
    }
}
