package com.serdar.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks almost all API access for frozen accounts. Frozen users may only
 * refresh their session, read /me, unfreeze, or log out.
 */
@Component
public class FrozenAccountFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && user.frozen()) {
            if (!allowedForFrozen(req.getMethod(), req.getRequestURI())) {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Account frozen\",\"code\":\"ACCOUNT_FROZEN\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean allowedForFrozen(String method, String path) {
        if ("POST".equals(method) && "/api/auth/logout".equals(path)) return true;
        if ("POST".equals(method) && "/api/auth/logout-all".equals(path)) return true;
        if ("POST".equals(method) && "/api/auth/refresh".equals(path)) return true;
        if ("GET".equals(method) && "/api/user/me".equals(path)) return true;
        if ("POST".equals(method) && "/api/user/unfreeze".equals(path)) return true;
        return false;
    }
}
