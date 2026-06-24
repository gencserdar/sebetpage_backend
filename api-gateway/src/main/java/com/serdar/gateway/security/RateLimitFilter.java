package com.serdar.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Distributed token-bucket rate limiter backed by Redis.
 *
 * One bucket is kept per (rule, client IP). Redis enforces the bucket
 * atomically through {@link RedisTokenBucketRateLimiter}, so the limits hold
 * across multiple gateway instances.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTokenBucketRateLimiter limiter;
    private final boolean trustProxyHeaders;
    private final List<Rule> rules;

    private record Rule(String name, HttpMethod method, Pattern pathPattern, long capacity, long refillMillis) {
        boolean matches(HttpServletRequest req) {
            return method.matches(req.getMethod()) && pathPattern.matcher(req.getRequestURI()).matches();
        }
    }

    public RateLimitFilter(
            RedisTokenBucketRateLimiter limiter,
            @Value("${app.trust-proxy-headers}") boolean trustProxyHeaders,
            @Value("${app.rate-limit.login.capacity}") long loginCapacity,
            @Value("${app.rate-limit.login.window-seconds}") long loginWindowSeconds,
            @Value("${app.rate-limit.register.capacity}") long registerCapacity,
            @Value("${app.rate-limit.register.window-seconds}") long registerWindowSeconds,
            @Value("${app.rate-limit.forgot-password.capacity}") long forgotCapacity,
            @Value("${app.rate-limit.forgot-password.window-seconds}") long forgotWindowSeconds,
            @Value("${app.rate-limit.request-email-change.capacity}") long requestEmailCapacity,
            @Value("${app.rate-limit.request-email-change.window-seconds}") long requestEmailWindowSeconds,
            @Value("${app.rate-limit.request-password-change.capacity}") long requestPasswordCapacity,
            @Value("${app.rate-limit.request-password-change.window-seconds}") long requestPasswordWindowSeconds,
            @Value("${app.rate-limit.activate.capacity}") long activateCapacity,
            @Value("${app.rate-limit.activate.window-seconds}") long activateWindowSeconds,
            @Value("${app.rate-limit.reset-password.capacity}") long resetCapacity,
            @Value("${app.rate-limit.reset-password.window-seconds}") long resetWindowSeconds,
            @Value("${app.rate-limit.messaging-group-write.capacity}") long messagingGroupWriteCapacity,
            @Value("${app.rate-limit.messaging-group-write.window-seconds}") long messagingGroupWriteWindowSeconds,
            @Value("${app.rate-limit.messaging-group-photo.capacity}") long messagingGroupPhotoCapacity,
            @Value("${app.rate-limit.messaging-group-photo.window-seconds}") long messagingGroupPhotoWindowSeconds,
            @Value("${app.rate-limit.chat-send.capacity}") long chatSendCapacity,
            @Value("${app.rate-limit.chat-send.window-seconds}") long chatSendWindowSeconds,
            @Value("${app.rate-limit.profile-settings-write.capacity:30}") long profileSettingsWriteCapacity,
            @Value("${app.rate-limit.profile-settings-write.window-seconds:60}") long profileSettingsWriteWindowSeconds,
            @Value("${app.rate-limit.profile-photo.capacity:10}") long profilePhotoCapacity,
            @Value("${app.rate-limit.profile-photo.window-seconds:300}") long profilePhotoWindowSeconds,
            @Value("${app.rate-limit.search.capacity:60}") long searchCapacity,
            @Value("${app.rate-limit.search.window-seconds:60}") long searchWindowSeconds,
            @Value("${app.rate-limit.friend-write.capacity:30}") long friendWriteCapacity,
            @Value("${app.rate-limit.friend-write.window-seconds:60}") long friendWriteWindowSeconds,
            @Value("${app.rate-limit.block-write.capacity:20}") long blockWriteCapacity,
            @Value("${app.rate-limit.block-write.window-seconds:60}") long blockWriteWindowSeconds,
            @Value("${app.rate-limit.account-delete.capacity:3}") long accountDeleteCapacity,
            @Value("${app.rate-limit.account-delete.window-seconds:3600}") long accountDeleteWindowSeconds
    ) {
        this.limiter = limiter;
        this.trustProxyHeaders = trustProxyHeaders;
        this.rules = List.of(
                exact("login",         HttpMethod.POST, "/api/auth/login",
                        loginCapacity,           loginWindowSeconds),
                exact("register",      HttpMethod.POST, "/api/auth/register",
                        registerCapacity,        registerWindowSeconds),
                exact("forgot",        HttpMethod.POST, "/api/auth/forgot-password",
                        forgotCapacity,          forgotWindowSeconds),
                exact("resend-act",    HttpMethod.POST, "/api/auth/resend-activation",
                        forgotCapacity,          forgotWindowSeconds),
                exact("request-email", HttpMethod.POST, "/api/user/request-email-change",
                        requestEmailCapacity,    requestEmailWindowSeconds),
                exact("request-pass",  HttpMethod.POST, "/api/user/request-password-change",
                        requestPasswordCapacity, requestPasswordWindowSeconds),
                exact("confirm-email", HttpMethod.POST, "/api/user/confirm-email-change",
                        requestEmailCapacity, requestEmailWindowSeconds),
                exact("confirm-pass",  HttpMethod.POST, "/api/user/confirm-password-change",
                        requestPasswordCapacity, requestPasswordWindowSeconds),
                exact("activate",      HttpMethod.POST, "/api/auth/activate",
                        activateCapacity,        activateWindowSeconds),
                exact("activate-get",  HttpMethod.GET,  "/api/auth/activate",
                        activateCapacity,        activateWindowSeconds),
                exact("reset",         HttpMethod.POST, "/api/auth/reset-password",
                        resetCapacity,           resetWindowSeconds),
                regex("messaging-group-photo", HttpMethod.POST, "^/api/messaging-groups/[^/]+/photo$",
                        messagingGroupPhotoCapacity, messagingGroupPhotoWindowSeconds),
                exact("messaging-group-write", HttpMethod.POST, "/api/messaging-groups",
                        messagingGroupWriteCapacity, messagingGroupWriteWindowSeconds),
                regex("messaging-group-write", HttpMethod.POST, "^/api/messaging-groups/[^/]+/members$",
                        messagingGroupWriteCapacity, messagingGroupWriteWindowSeconds),
                regex("messaging-group-write", HttpMethod.PATCH, "^/api/messaging-groups/[^/]+$",
                        messagingGroupWriteCapacity, messagingGroupWriteWindowSeconds),
                regex("messaging-group-write", HttpMethod.PATCH, "^/api/messaging-groups/[^/]+/participants/[^/]+$",
                        messagingGroupWriteCapacity, messagingGroupWriteWindowSeconds),
                regex("messaging-group-write", HttpMethod.DELETE, "^/api/messaging-groups/[^/]+(/members/[^/]+)?$",
                        messagingGroupWriteCapacity, messagingGroupWriteWindowSeconds),
                regex("chat-send",     HttpMethod.POST, "^/api/chat/conversations/[^/]+/send$",
                        chatSendCapacity,        chatSendWindowSeconds),
                exact("profile-settings-write", HttpMethod.PUT, "/api/user/profile-settings",
                        profileSettingsWriteCapacity, profileSettingsWriteWindowSeconds),
                exact("profile-photo", HttpMethod.POST, "/api/user/profile-photo",
                        profilePhotoCapacity, profilePhotoWindowSeconds),
                exact("search", HttpMethod.GET, "/api/search",
                        searchCapacity, searchWindowSeconds),
                exact("community-search", HttpMethod.GET, "/api/communities/search",
                        searchCapacity, searchWindowSeconds),
                exact("friend-write", HttpMethod.POST, "/api/friend-requests/send",
                        friendWriteCapacity, friendWriteWindowSeconds),
                regex("friend-write", HttpMethod.POST, "^/api/friend-requests/[^/]+/respond$",
                        friendWriteCapacity, friendWriteWindowSeconds),
                regex("friend-write", HttpMethod.DELETE, "^/api/friend-requests/[^/]+/cancel$",
                        friendWriteCapacity, friendWriteWindowSeconds),
                regex("friend-write", HttpMethod.DELETE, "^/api/friends/remove/[^/]+$",
                        friendWriteCapacity, friendWriteWindowSeconds),
                regex("block-write", HttpMethod.POST, "^/api/blocks/[^/]+$",
                        blockWriteCapacity, blockWriteWindowSeconds),
                regex("block-write", HttpMethod.DELETE, "^/api/blocks/[^/]+$",
                        blockWriteCapacity, blockWriteWindowSeconds),
                exact("account-delete", HttpMethod.DELETE, "/api/user/account",
                        accountDeleteCapacity, accountDeleteWindowSeconds)
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = match(req);
        if (rule == null) {
            chain.doFilter(req, res);
            return;
        }

        String key = "rl:" + rule.name() + ":" + clientKey(req);
        boolean allow = limiter.tryAcquireMillis(key, rule.capacity(), rule.refillMillis());

        if (!allow) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(Math.max(1L, rule.refillMillis() / 1000L)));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests, slow down.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private Rule match(HttpServletRequest req) {
        for (Rule r : rules) {
            if (r.matches(req)) return r;
        }
        return null;
    }

    private static Rule exact(String name, HttpMethod method, String path, long capacity, long windowSeconds) {
        return regex(name, method, "^" + Pattern.quote(path) + "$", capacity, windowSeconds);
    }

    private static Rule regex(String name, HttpMethod method, String pathRegex, long capacity, long windowSeconds) {
        return new Rule(name, method, Pattern.compile(pathRegex), capacity,
                RedisTokenBucketRateLimiter.refillMillis(capacity, windowSeconds));
    }

    private String clientKey(HttpServletRequest req) {
        String xff = trustProxyHeaders ? req.getHeader("X-Forwarded-For") : null;
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
