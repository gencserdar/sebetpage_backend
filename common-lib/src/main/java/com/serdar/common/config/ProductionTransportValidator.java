package com.serdar.common.config;

/**
 * Refuses to boot in production-like environments when internal gRPC is left on PLAINTEXT.
 */
public final class ProductionTransportValidator {

    private ProductionTransportValidator() {}

    public static void requireSecureGrpcInProd(String environment,
                                               String clientNegotiationType,
                                               boolean serverSecurityEnabled) {
        if (!isProductionLike(environment)) return;
        if ("PLAINTEXT".equalsIgnoreCase(clientNegotiationType) && !serverSecurityEnabled) {
            throw new IllegalStateException(
                    "Production environment requires TLS for internal gRPC " +
                    "(set GRPC_CLIENT_NEGOTIATION_TYPE=TLS and GRPC_SERVER_SECURITY_ENABLED=true)");
        }
    }

    public static boolean isProductionLike(String env) {
        if (env == null) return false;
        String normalized = env.trim().toLowerCase();
        return normalized.equals("prod") || normalized.equals("production");
    }
}
