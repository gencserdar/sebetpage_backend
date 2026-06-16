package com.serdar.auth.config;

import com.serdar.common.config.ProductionSecretsValidator;
import com.serdar.common.config.ProductionTransportValidator;
import com.serdar.common.grpc.InternalGrpcActorServerInterceptor;
import com.serdar.common.grpc.InternalGrpcClientInterceptor;
import com.serdar.common.grpc.InternalGrpcServerInterceptor;
import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import jakarta.annotation.PostConstruct;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcSecurityConfig {

    @Value("${app.environment}") private String environment;
    @Value("${grpc.client.GLOBAL.negotiationType}") private String clientNegotiationType;
    @Value("${grpc.server.security.enabled}") private boolean serverSecurityEnabled;
    @Value("${app.internal-grpc-token}") private String internalGrpcToken;
    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${refresh.hmac-key}") private String refreshHmacKey;
    @Value("${spring.datasource.password}") private String dbPassword;

    @PostConstruct
    void validateProductionSecurity() {
        ProductionTransportValidator.requireSecureGrpcInProd(
                environment, clientNegotiationType, serverSecurityEnabled);
        ProductionSecretsValidator.requireSecret(environment, "INTERNAL_GRPC_TOKEN", internalGrpcToken);
        ProductionSecretsValidator.requireSecret(environment, "JWT_SECRET", jwtSecret);
        ProductionSecretsValidator.requireSecret(environment, "REFRESH_TOKEN_HMAC_KEY", refreshHmacKey);
        ProductionSecretsValidator.requireSecret(environment, "DB_PASSWORD", dbPassword);
    }

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor internalGrpcAuthClientInterceptor() {
        return new InternalGrpcClientInterceptor(internalGrpcToken);
    }

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor internalGrpcAuthServerInterceptor() {
        return new InternalGrpcServerInterceptor(internalGrpcToken);
    }

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor internalGrpcActorServerInterceptor() {
        return new InternalGrpcActorServerInterceptor();
    }
}
