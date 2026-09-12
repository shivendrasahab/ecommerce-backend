package com.shivendra.ecommerce.security;

import com.shivendra.ecommerce.exceptions.UnauthorizedException;
import com.shivendra.ecommerce.models.User;
import com.shivendra.ecommerce.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates the bearer token on protected routes and exposes the resolved {@link User} to
 * controllers via {@link #USER_ATTRIBUTE}. Kept deliberately small: one lookup per request,
 * no framework magic, easy to reason about in a review.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE = "authenticatedUser";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Missing or malformed Authorization header");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        User user = authService.validateToken(token);
        request.setAttribute(USER_ATTRIBUTE, user);
        return true;
    }
}
