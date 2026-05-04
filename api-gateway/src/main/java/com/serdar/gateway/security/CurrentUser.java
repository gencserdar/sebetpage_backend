package com.serdar.gateway.security;

import com.serdar.common.ServiceException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthenticatedUser require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u))
            throw ServiceException.unauth("Not authenticated");
        return u;
    }
}
