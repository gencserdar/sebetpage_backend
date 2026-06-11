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
            @Value("${app.rate-limit.chat-send.window-seconds}") long chatSendWindowSeconds
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
                exact("request-email", HttpMethod.POST, "/api/user/request-email-change",
                        requestEmailCapacity,    requestEmailWindowSeconds),
                exact("request-pass",  HttpMethod.POST, "/api/user/request-password-change",
                        requestPasswordCapacity, requestPasswordWindowSeconds),
                exact("activate",      HttpMethod.POST, "/api/auth/activate",
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
                        chatSendCapacity,        chatSendWindowSeconds)
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
