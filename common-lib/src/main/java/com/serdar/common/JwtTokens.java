package com.serdar.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Thin wrapper around jjwt so services can sign/verify tokens with the same
 * shared secret. Auth-service issues tokens; gateway + chat-service verify them
 * (gateway typically does this via a gRPC call into auth-service, but having
 * local verification helpers avoids a network hop for low-stakes checks).
 */
public final class JwtTokens {

    private JwtTokens() {}

    public static SecretKey key(String base64Secret) {
        byte[] bytes = Decoders.BASE64.decode(base64Secret);
        return Keys.hmacShaKeyFor(bytes);
    }

    public static String issue(SecretKey key, String subject, Map<String, Object> claims, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    public static Claims parse(SecretKey key, String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public static <T> T extract(SecretKey key, String token, Function<Claims, T> f) {
        return f.apply(parse(key, token));
    }

    public static boolean isExpired(SecretKey key, String token) {
        try {
            return parse(key, token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
