package com.smartfarm.web;

import com.smartfarm.service.AuthService;
import com.smartfarm.service.FarmAccessResolver;
import com.smartfarm.service.Tenancy;
import com.smartfarm.service.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires "Authorization: Bearer <token>", exposes the user id as a request attribute, and fixes which farm the
 * request works on (see FarmAccessResolver and Tenancy). Company staff choose a farm with the X-Farm-Id header.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "userId";

    private final AuthService authService;
    private final FarmAccessResolver farmAccess;

    public AuthInterceptor(AuthService authService, FarmAccessResolver farmAccess) {
        this.authService = authService;
        this.farmAccess = farmAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        Long userId = authService.resolve(bearerToken(request))
                .orElseThrow(() -> new UnauthorizedException("Please log in"));
        request.setAttribute(USER_ID, userId);
        boolean accountOnly = request.getRequestURI().startsWith("/api/auth/");
        Tenancy.set(farmAccess.resolve(userId, request.getHeader("X-Farm-Id"), accountOnly));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Tenancy.clear();
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
