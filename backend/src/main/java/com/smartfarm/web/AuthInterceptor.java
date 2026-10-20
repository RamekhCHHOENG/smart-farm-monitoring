package com.smartfarm.web;

import com.smartfarm.service.AuthService;
import com.smartfarm.service.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Requires "Authorization: Bearer <token>" and exposes the user id as a request attribute. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "userId";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        Long userId = authService.resolve(bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Please log in"));
        request.setAttribute(USER_ID, userId);
        return true;
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
