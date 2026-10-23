package com.smartfarm.web;

import com.smartfarm.service.UserService;
import com.smartfarm.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff accounts, managed by the Farm Owner. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return userService.list(userId).stream().map(UserDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @Valid @RequestBody UserRequest req) {
        return UserDto.of(userService.create(userId, req.username(), req.name(), req.role(), req.password()));
    }

    @PutMapping("/{id}")
    public UserDto update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                          @Valid @RequestBody UserUpdateRequest req) {
        return UserDto.of(userService.update(userId, id, req.name(), req.role()));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(userId, id, req.newPassword());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long id) {
        userService.delete(userId, id);
    }
}
