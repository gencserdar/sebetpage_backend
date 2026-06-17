package com.serdar.gateway.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fast revocation marker for WebSocket/STOMP paths. HTTP auth already re-checks
 * the session row on every {@code validate} call; this avoids a gRPC round-trip
 * on every chat frame while still blocking sends immediately after revoke.
 */
@Component
public class SessionRevocationCache {

    private static final String KEY_PREFIX = "session:revoked:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    public SessionRevocationCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void markRevoked(long sessionId) {
        if (sessionId <= 0) return;
        try {
            redis.opsForValue().set(KEY_PREFIX + sessionId, "1", TTL);
        } catch (Exception ignored) {
            // Fail open for cache — validate() still blocks HTTP on next request.
        }
    }

    public boolean isRevoked(long sessionId) {
        if (sessionId <= 0) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
        } catch (Exception e) {
            return false;
        }
    }
}
