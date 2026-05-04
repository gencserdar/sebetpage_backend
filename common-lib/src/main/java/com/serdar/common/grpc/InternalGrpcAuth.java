package com.serdar.common.grpc;

import io.grpc.Metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class InternalGrpcAuth {

    static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-grpc-token", Metadata.ASCII_STRING_MARSHALLER);

    private InternalGrpcAuth() {}

    static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("app.internal-grpc-token must be set");
        }
        return token.trim();
    }

    static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
