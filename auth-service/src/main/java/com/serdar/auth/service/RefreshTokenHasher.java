package com.serdar.auth.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Hashes refresh tokens for at-rest storage.
 *
 * Why HMAC-SHA-256 (not bcrypt):
 *
 *   Refresh tokens are 256-bit random JWTs we issue ourselves. Their
 *   entropy is already astronomical — a brute-force search needs more
 *   work than the heat death of the universe. Bcrypt's slowness exists
 *   to defend low-entropy human passwords; using it here just burns
 *   ~200ms of CPU per refresh for zero security gain.
 *
 *   HMAC-SHA-256 (microseconds, not milliseconds) is the standard for
 *   high-entropy tokens at rest. The HMAC key is a server secret stored
 *   separately from the DB, so a leaked DB row alone can't even compute
 *   "what hash would token X produce?" without also stealing the key.
 *
 * Bcrypt stays in place for ACTUAL passwords (Credential.password,
 * pendingPasswordNewHash). This class is only for refresh tokens.
 *
 * Migration: existing rows store bcrypt hashes (recognisable by the
 * `$2a$` / `$2b$` prefix). {@link #matches(String, String)} accepts
 * either format and returns true for legacy bcrypt matches; callers
 * then re-write the hash in HMAC format on the next save, draining the
 * old format out over time without forcing logouts.
 */
@Component
public class RefreshTokenHasher {

    private static final String ALGO = "HmacSHA256";

    /**
     * Server-side HMAC key. Required from `.env` (REFRESH_TOKEN_HMAC_KEY).
     * Should be base64-encoded random bytes — generate with
     * {@code openssl rand -base64 32}. Treat as a secret; rotating it
     * invalidates every active session (every refresh token's stored
     * hash stops matching), so plan accordingly if you ever rotate.
     */
    @Value("${refresh.hmac-key}")
    private String hmacKeyBase64;

    private byte[] hmacKey;

    /** Lazy because Spring property injection happens after the constructor. */
    @PostConstruct
    void init() {
        if (hmacKeyBase64 == null || hmacKeyBase64.isBlank()) {
            throw new IllegalStateException("REFRESH_TOKEN_HMAC_KEY required");
        }
        try {
            this.hmacKey = Base64.getDecoder().decode(hmacKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("REFRESH_TOKEN_HMAC_KEY must be base64-encoded", e);
        }
        if (this.hmacKey.length < 32) {
            throw new IllegalStateException("REFRESH_TOKEN_HMAC_KEY must decode to at least 32 bytes");
        }
    }

    /** Returns the canonical at-rest representation: base64(HMAC-SHA-256(token)). */
    public String hash(String token) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(hmacKey, ALGO));
            byte[] out = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /**
     * Compare a submitted cleartext token against a stored hash.
     *
     * Accepts BOTH the new HMAC format and the legacy bcrypt format
     * (rows written before this class existed). The bcrypt branch is
     * the migration path: returns true on match, and the caller should
     * re-write the row with {@link #hash(String)} immediately so the
     * legacy format drains out.
     *
     * Constant-time comparison on the HMAC branch defeats theoretical
     * timing-side-channel poking; bcrypt's own matcher is constant-time
     * on its branch.
     */
    public boolean matches(String submittedToken, String storedHash) {
        if (submittedToken == null || storedHash == null) return false;
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            // Legacy bcrypt — verify with the standard bcrypt routine via
            // the static helper to avoid taking a Spring dep here. The
            // BCryptPasswordEncoder verifier is what was used to write
            // these rows.
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(submittedToken, storedHash);
        }
        String fresh = hash(submittedToken);
        return MessageDigest.isEqual(
                fresh.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** Whether the stored hash is in the legacy bcrypt format and should
     *  be rewritten on the next save. Used by callers to lazy-migrate. */
    public boolean isLegacyFormat(String storedHash) {
        return storedHash != null
                && (storedHash.startsWith("$2a$")
                || storedHash.startsWith("$2b$")
                || storedHash.startsWith("$2y$"));
    }
}
