package com.serdar.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.serdar.auth.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Write-through / read-through Redis cache for session rows.
 *
 * Why cache sessions?
 *   The refresh endpoint is the hottest auth path — called every ~1 minute per
 *   active tab. Without a cache every refresh hits MySQL with a full-table-scan-
 *   resistant hash lookup. With the cache, that becomes a single Redis GET.
 *
 * Key format  :  session:{tokenHash}
 * Value       :  Jackson-serialised Session (ISO-8601 date strings)
 * TTL         :  derived from session.expiresAt — entry auto-expires when the
 *                JWT it covers would have expired anyway.
 *
 * Failure policy — fail-open.
 *   Any Redis error degrades gracefully to a DB hit; the service keeps working.
 *   Log a WARN so the ops team can catch Redis connectivity issues early.
 */
@Slf4j
@Component
public class SessionCacheService {

    static final String PREFIX = "session:";

    // Self-contained ObjectMapper so this service doesn't depend on whatever
    // the application-level bean has configured for Jackson date formatting.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final StringRedisTemplate redis;

    public SessionCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Look up a session by its token hash. Returns empty on a cache miss OR on
     * any Redis error (the caller must fall back to the DB in both cases).
     */
    public Optional<Session> findByTokenHash(String tokenHash) {
        try {
            String json = redis.opsForValue().get(PREFIX + tokenHash);
            if (json == null) return Optional.empty();
            return Optional.of(MAPPER.readValue(json, Session.class));
        } catch (Exception e) {
            log.warn("Redis session cache read failed (key={}): {}", tokenHash, e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Cache a session. TTL is derived from {@code session.expiresAt} so the
     * Redis entry disappears automatically when the JWT it covers expires.
     * No-op if the TTL is already in the past.
     */
    public void put(Session session) {
        try {
            long ttlSeconds = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(ZoneOffset.UTC), session.getExpiresAt());
            if (ttlSeconds <= 0) return;
            redis.opsForValue().set(
                    PREFIX + session.getTokenHash(),
                    MAPPER.writeValueAsString(session),
                    Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis session cache write failed (sessionId={}): {}",
                    session.getId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Evict
    // -------------------------------------------------------------------------

    /** Remove one session entry by its token hash (single-device logout). */
    public void evict(String tokenHash) {
        try {
            redis.delete(PREFIX + tokenHash);
        } catch (Exception e) {
            log.warn("Redis session cache evict failed (key={}): {}", tokenHash, e.getMessage());
        }
    }

    /** Remove multiple session entries at once (logout-all / password reset). */
    public void evictAll(Collection<String> tokenHashes) {
        if (tokenHashes == null || tokenHashes.isEmpty()) return;
        try {
            List<String> keys = tokenHashes.stream()
                    .map(h -> PREFIX + h)
                    .toList();
            redis.delete(keys);
        } catch (Exception e) {
            log.warn("Redis session cache evict-all failed: {}", e.getMessage());
        }
    }
}
