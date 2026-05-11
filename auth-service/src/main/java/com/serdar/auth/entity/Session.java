package com.serdar.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row = one active browser/device session.
 *
 * Replaces the single refresh_token + remember_me columns that used to sit on
 * the credentials table. This lets a single user be signed in on multiple
 * devices simultaneously — each device holds its own (session_id, refresh JWT)
 * pair, and a logout only invalidates that specific row.
 *
 * token_hash  — HMAC-SHA-256 of the raw refresh JWT. Same rationale as before:
 *               HMAC is the right choice for high-entropy 256-bit random tokens;
 *               bcrypt's brute-force resistance buys nothing here.
 * expires_at  — mirrors the JWT's own expiry. Lets a scheduled cleanup job
 *               delete stale rows without parsing every JWT, and acts as a
 *               fallback safety net if JWT expiry were ever misconfigured.
 * remember_me — captured at login; used on every refresh to decide whether to
 *               issue a 2-day or 30-day sliding window.
 */
@Entity
@Table(
    name = "sessions",
    indexes = @Index(name = "idx_sessions_user_id", columnList = "user_id")
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "remember_me", nullable = false)
    @Builder.Default
    private Boolean rememberMe = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
