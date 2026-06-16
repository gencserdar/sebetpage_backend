package com.serdar.common.config;

import java.util.Locale;
import java.util.Set;

/** Refuses to boot in production when crypto/signing secrets are left at dev placeholders. */
public final class ProductionSecretsValidator {

    private static final Set<String> WEAK = Set.of(
            "change-me", "changeme", "root", "password", "secret", "test", "123456", "admin");

    private ProductionSecretsValidator() {}

    public static void requireSecret(String environment, String name, String value) {
        if (!ProductionTransportValidator.isProductionLike(environment)) {
            return;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production requires " + name);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (WEAK.contains(normalized) || value.trim().length() < 16) {
            throw new IllegalStateException(
                    "Production requires a strong " + name + " (min 16 chars, not a placeholder)");
        }
    }
}
