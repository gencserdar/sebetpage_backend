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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory token-bucket limiter for auth-adjacent endpoints.
 *
 * One bucket is kept per (rule, client IP). This is single-instance state; use
 * a Redis-backed implementation before running multiple gateway replicas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final boolean trustProxyHeaders;
    private final List<Rule> rules;
    private final Map<String, Map<String, Bucket>> buckets = new ConcurrentHashMap<>();

    private static class Bucket {
        final long capacity;
        final long refillIntervalMillis;
        final AtomicLong tokens;
        final AtomicLong lastRefillMillis;

        Bucket(long capacity, long refillIntervalMillis) {
            this.capacity = capacity;
            this.refillIntervalMillis = refillIntervalMillis;
            this.tokens = new AtomicLong(capacity);
            this.lastRefillMillis = new AtomicLong(System.currentTimeMillis());
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillMillis.get();
            if (elapsed >= refillIntervalMillis) {
                long ticks = elapsed / refillIntervalMillis;
                long current = tokens.get();
                tokens.set(Math.min(capacity, current + ticks));
                lastRefillMillis.set(lastRefillMillis.get() + ticks * refillIntervalMillis);
            }
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }
    }

    private record Rule(String name, HttpMethod method, String pathPrefix, long capacity, long refillMillis) {
        boolean matches(HttpServletRequest req) {
            return method.matches(req.getMethod()) && req.getRequestURI().startsWith(pathPrefix);
        }
    }

    public RateLimitFilter(
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
            @Value("${app.rate-limit.reset-password.window-seconds}") long resetWindowSeconds
    ) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.rules = List.of(
                new Rule("login", HttpMethod.POST, "/api/auth/login",
                        loginCapacity, refillMillis(loginCapacity, loginWindowSeconds)),
                new Rule("register", HttpMethod.POST, "/api/auth/register",
                        registerCapacity, refillMillis(registerCapacity, registerWindowSeconds)),
                new Rule("forgot", HttpMethod.POST, "/api/auth/forgot-password",
                        forgotCapacity, refillMillis(forgotCapacity, forgotWindowSeconds)),
                new Rule("request-email", HttpMethod.POST, "/api/user/request-email-change",
                        requestEmailCapacity, refillMillis(requestEmailCapacity, requestEmailWindowSeconds)),
                new Rule("request-pass", HttpMethod.POST, "/api/user/request-password-change",
                        requestPasswordCapacity, refillMillis(requestPasswordCapacity, requestPasswordWindowSeconds)),
                new Rule("activate", HttpMethod.GET, "/api/auth/activate",
                        activateCapacity, refillMillis(activateCapacity, activateWindowSeconds)),
                new Rule("reset", HttpMethod.POST, "/api/auth/reset-password",
                        resetCapacity, refillMillis(resetCapacity, resetWindowSeconds))
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

        String key = clientKey(req);
        Bucket bucket = buckets
                .computeIfAbsent(rule.name(), n -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> new Bucket(rule.capacity(), rule.refillMillis()));

        if (!bucket.tryAcquire()) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(Math.max(1L, rule.refillMillis() / 1000L)));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests, slow down.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private Rule match(HttpServletRequest req) {
        for (Rule rule : rules) {
            if (rule.matches(req)) return rule;
        }
        return null;
    }

    private static long refillMillis(long capacity, long windowSeconds) {
        if (capacity <= 0) throw new IllegalStateException("Rate limit capacity must be positive");
        if (windowSeconds <= 0) throw new IllegalStateException("Rate limit window must be positive");
        long windowMillis = Math.multiplyExact(windowSeconds, 1000L);
        return Math.max(1L, windowMillis / capacity);
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
