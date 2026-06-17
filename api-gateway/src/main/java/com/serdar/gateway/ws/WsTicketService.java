package com.serdar.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class WsTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "ws-ticket:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public WsTicketService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.ws-ticket-ttl-seconds}") long ttlSeconds
    ) {
        if (ttlSeconds <= 0) throw new IllegalStateException("app.ws-ticket-ttl-seconds must be positive");
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedTicket issue(long userId, long sessionId, String email, String nickname,
                              String clientAddress, String userAgent) {
        String value = newTicketValue();
        StoredTicket ticket = new StoredTicket(
                userId,
                sessionId,
                email,
                nickname,
                normalize(clientAddress),
                normalize(userAgent),
                Instant.now().plusSeconds(ttlSeconds).toEpochMilli()
        );
        try {
            redis.opsForValue().set(redisKey(value), objectMapper.writeValueAsString(ticket), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("Could not issue WebSocket ticket", e);
        }
        return new IssuedTicket(value, ttlSeconds);
    }

    public Optional<Ticket> consume(String value, String clientAddress, String userAgent) {
        if (value == null || value.isBlank()) return Optional.empty();
        String raw;
        try {
            raw = redis.opsForValue().getAndDelete(redisKey(value));
        } catch (Exception e) {
            return Optional.empty();
        }
        if (raw == null || raw.isBlank()) return Optional.empty();

        StoredTicket stored;
        try {
            stored = objectMapper.readValue(raw, StoredTicket.class);
        } catch (Exception e) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.ofEpochMilli(stored.expiresAtMillis());
        if (expiresAt.isBefore(Instant.now())) return Optional.empty();
        if (!stored.clientAddress().equals(normalize(clientAddress))) return Optional.empty();
        if (!stored.userAgent().equals(normalize(userAgent))) return Optional.empty();

        return Optional.of(new Ticket(
                stored.userId(),
                stored.sessionId(),
                stored.email(),
                stored.nickname(),
                stored.clientAddress(),
                stored.userAgent(),
                expiresAt
        ));
    }

    private static String redisKey(String ticket) {
        return KEY_PREFIX + ticket;
    }

    private static String newTicketValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record IssuedTicket(String value, long expiresInSeconds) {}
    public record Ticket(
            long userId,
            long sessionId,
            String email,
            String nickname,
            String clientAddress,
            String userAgent,
            Instant expiresAt
    ) {}
    record StoredTicket(
            long userId,
            long sessionId,
            String email,
            String nickname,
            String clientAddress,
            String userAgent,
            long expiresAtMillis
    ) {}
}
