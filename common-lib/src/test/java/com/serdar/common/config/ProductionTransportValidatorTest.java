package com.serdar.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionTransportValidatorTest {

    @Test
    void allowsPlaintextInDev() {
        assertDoesNotThrow(() ->
                ProductionTransportValidator.requireSecureGrpcInProd("dev", "PLAINTEXT", false));
    }

    @Test
    void rejectsPlaintextInProduction() {
        assertThrows(IllegalStateException.class, () ->
                ProductionTransportValidator.requireSecureGrpcInProd("prod", "PLAINTEXT", false));
    }

    @Test
    void allowsTlsInProduction() {
        assertDoesNotThrow(() ->
                ProductionTransportValidator.requireSecureGrpcInProd("production", "TLS", true));
    }
}
