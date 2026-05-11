package com.serdar.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Distributed token-bucket rate limiter backed by Redis.
 *
 * One bucket is kept per (rule, client IP) inside Redis with a TTL, so the
 * limits are enforced correctly across multiple gateway instances. The bucket
 * is implemented as an atomic Lua script — no race window between read and
 * write because Redis executes Lua atomically.
 *
 * Key format : "rl:{ruleName}:{clientIp}"
 * TTL        : 2× the full refill window so idle buckets expire on their own.
 *
 * Redis failure policy — fail-open with a logged warning. If Redis is
 * unreachable the request is allowed through rather than hard-failing the
 * user. A total Redis outage causing a rate-limit bypass is a worse
 * trade-off for availability than a short window of unthrottled traffic.
 * Monitor Redis health separately and alert on connection errors.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    // Lua token-bucket script.
    // KEYS[1] = bucket key
    // ARGV[1] = capacity  (max tokens)
    // ARGV[2] = refill interval in ms per token
    // ARGV[3] = current epoch-ms
    // Returns 1 → allowed, 0 → rate-limited.
    private static final String LUA_SCRIPT =
        "local key        = KEYS[1]\n" +
        "local capacity   = tonumber(ARGV[1])\n" +
        "local refill_ms  = tonumber(ARGV[2])\n" +
        "local now        = tonumber(ARGV[3])\n" +
        "local data       = redis.call('HMGET', key, 'tokens', 'ts')\n" +
        "local tokens     = tonumber(data[1])\n" +
        "local ts         = tonumber(data[2])\n" +
        "if tokens == nil then\n" +
        "  tokens = capacity - 1\n" +
        "  redis.call('HMSET', key, 'tokens', tokens, 'ts', now)\n" +
        "  redis.call('PEXPIRE', key, capacity * refill_ms * 2)\n" +
        "  return 1\n" +
        "end\n" +
        "local elapsed = now - ts\n" +
        "local refill  = math.floor(elapsed / refill_ms)\n" +
        "if refill > 0 then\n" +
        "  tokens = math.min(capacity, tokens + refill)\n" +
        "  ts     = ts + refill * refill_ms\n" +
        "end\n" +
        "if tokens > 0 then\n" +
        "  tokens = tokens - 1\n" +
        "  redis.call('HMSET', key, 'tokens', tokens, 'ts', ts)\n" +
        "  redis.call('PEXPIRE', key, capacity * refill_ms * 2)\n" +
        "  return 1\n" +
        "else\n" +
        "  redis.call('HMSET', key, 'tokens', 0, 'ts', ts)\n" +
        "  redis.call('PEXPIRE', key, refill_ms)\n" +
        "  return 0\n" +
        "end\n";

    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setScriptText(LUA_SCRIPT);
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final boolean trustProxyHeaders;
    private final List<Rule> rules;

    private record Rule(String name, HttpMethod method, String pathPrefix, long capacity, long refillMillis) {
        boolean matches(HttpServletRequest req) {
            return method.matches(req.getMethod()) && req.getRequestURI().startsWith(pathPrefix);
        }
    }

    public RateLimitFilter(
            StringRedisTemplate redis,
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
        this.redis = redis;
        this.trustProxyHeaders = trustProxyHeaders;
        this.rules = List.of(
                new Rule("login",         HttpMethod.POST, "/api/auth/login",
                        loginCapacity,           refillMillis(loginCapacity,           loginWindowSeconds)),
                new Rule("register",      HttpMethod.POST, "/api/auth/register",
                        registerCapacity,        refillMillis(registerCapacity,        registerWindowSeconds)),
                new Rule("forgot",        HttpMethod.POST, "/api/auth/forgot-password",
                        forgotCapacity,          refillMillis(forgotCapacity,          forgotWindowSeconds)),
                new Rule("request-email", HttpMethod.POST, "/api/user/request-email-change",
                        requestEmailCapacity,    refillMillis(requestEmailCapacity,    requestEmailWindowSeconds)),
                new Rule("request-pass",  HttpMethod.POST, "/api/user/request-password-change",
                        requestPasswordCapacity, refillMillis(requestPasswordCapacity, requestPasswordWindowSeconds)),
                new Rule("activate",      HttpMethod.GET,  "/api/auth/activate",
                        activateCapacity,        refillMillis(activateCapacity,        activateWindowSeconds)),
                new Rule("reset",         HttpMethod.POST, "/api/auth/reset-password",
                        resetCapacity,           refillMillis(resetCapacity,           resetWindowSeconds))
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

        String key    = "rl:" + rule.name() + ":" + clientKey(req);
        boolean allow = tryAcquire(key, rule);

        if (!allow) {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(Math.max(1L, rule.refillMillis() / 1000L)));
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests, slow down.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    /** Execute the Lua token-bucket script. Fail-open on any Redis error. */
    private boolean tryAcquire(String key, Rule rule) {
        try {
            Long result = redis.execute(
                    SCRIPT,
                    List.of(key),
                    String.valueOf(rule.capacity()),
                    String.valueOf(rule.refillMillis()),
                    String.valueOf(System.currentTimeMillis())
            );
            // null means the script threw an unexpected error → fail-open
            return !Long.valueOf(0L).equals(result);
        } catch (Exception e) {
            log.warn("Redis rate-limit unavailable for key '{}', failing open: {}", key, e.getMessage());
            return true;
        }
    }

    private Rule match(HttpServletRequest req) {
        for (Rule r : rules) { if (r.matches(req)) return r; }
        return null;
    }

    private static long refillMillis(long capacity, long windowSeconds) {
        if (capacity <= 0)      throw new IllegalStateException("Rate limit capacity must be positive");
        if (windowSeconds <= 0) throw new IllegalStateException("Rate limit window must be positive");
        return Math.max(1L, Math.multiplyExact(windowSeconds, 1000L) / capacity);
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
