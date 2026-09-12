package com.shivendra.ecommerce.security;

import com.shivendra.ecommerce.exceptions.UnauthorizedException;
import com.shivendra.ecommerce.models.User;
import jakarta.servlet.http.HttpServletRequest;

/** Tiny helper so controllers read the authenticated user in one line. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static User from(HttpServletRequest request) {
        Object user = request.getAttribute(AuthInterceptor.USER_ATTRIBUTE);
        if (user instanceof User u) {
            return u;
        }
        throw new UnauthorizedException("No authenticated user on request");
    }
}
