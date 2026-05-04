package com.serdar.gateway.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WsTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public WsTicketService(@Value("${app.ws-ticket-ttl-seconds}") long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalStateException("app.ws-ticket-ttl-seconds must be positive");
        this.ttlSeconds = ttlSeconds;
    }

    public IssuedTicket issue(long userId, String email, String nickname, String clientAddress, String userAgent) {
        cleanupExpired();
        String value = newTicketValue();
        tickets.put(value, new Ticket(
                userId,
                email,
                nickname,
                normalize(clientAddress),
                normalize(userAgent),
                Instant.now().plusSeconds(ttlSeconds)
        ));
        return new IssuedTicket(value, ttlSeconds);
    }

    public Optional<Ticket> consume(String value, String clientAddress, String userAgent) {
        if (value == null || value.isBlank()) return Optional.empty();
        Ticket ticket = tickets.remove(value);
        if (ticket == null) return Optional.empty();
        if (ticket.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!ticket.clientAddress().equals(normalize(clientAddress))) return Optional.empty();
        if (!ticket.userAgent().equals(normalize(userAgent))) return Optional.empty();
        return Optional.of(ticket);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
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
            String email,
            String nickname,
            String clientAddress,
            String userAgent,
            Instant expiresAt
    ) {}
}
