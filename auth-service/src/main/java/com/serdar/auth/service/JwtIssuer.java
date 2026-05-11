package com.serdar.auth.service;

import com.serdar.auth.entity.Credential;
import com.serdar.common.JwtTokens;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Map;

/**
 * Issues and verifies JWTs. Mirrors the monolith's JwtService logic; the
 * gateway will verify tokens by calling ValidateToken on auth-service, while
 * chat-service reuses the same secret locally to accept WS handshakes.
 */
@Service
public class JwtIssuer {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long accessTtlMs;

    private SecretKey key;

    @PostConstruct
    void init() { this.key = JwtTokens.key(secret); }

    public SecretKey key() { return key; }

    /**
     * @param sessionId  the sessions.id for the active session — embedded as
     *                   the "sid" claim so the gateway can route single-device
     *                   logout without a separate cookie or DB lookup.
     */
    public String issueAccess(Credential c, long sessionId) {
        return JwtTokens.issue(key, c.getEmail(),
                Map.of("role", c.getRole().name(), "uid", c.getId(), "sid", sessionId),
                accessTtlMs);
    }

    public String issueRefresh(Credential c, long sessionId, int cookieAgeSeconds) {
        return JwtTokens.issue(key, c.getEmail(),
                Map.of("role", c.getRole().name(), "uid", c.getId(),
                        "sid", sessionId, "type", "refresh"),
                cookieAgeSeconds * 1000L);
    }

    public Claims parse(String token) {
        return JwtTokens.parse(key, token);
    }

    public boolean isExpired(String token) {
        return JwtTokens.isExpired(key, token);
    }
}
