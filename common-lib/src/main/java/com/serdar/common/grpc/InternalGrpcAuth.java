package com.serdar.common.grpc;

import io.grpc.Metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalGrpcAuth {

    public static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-grpc-token", Metadata.ASCII_STRING_MARSHALLER);

    /** Set by api-gateway from the authenticated JWT subject (user id). */
    public static final Metadata.Key<String> GATEWAY_USER_ID_HEADER =
            Metadata.Key.of("x-gateway-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private InternalGrpcAuth() {}

    public static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("app.internal-grpc-token must be set");
        }
        return token.trim();
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public static Long parseGatewayUserId(Metadata headers) {
        if (headers == null) return null;
        String raw = headers.get(GATEWAY_USER_ID_HEADER);
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
