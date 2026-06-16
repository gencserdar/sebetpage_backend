package com.serdar.gateway.config;

import com.serdar.common.config.ProductionSecretsValidator;
import com.serdar.common.config.ProductionTransportValidator;
import com.serdar.gateway.grpc.GatewayGrpcClientInterceptor;
import io.grpc.ClientInterceptor;
import jakarta.annotation.PostConstruct;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcSecurityConfig {

    @Value("${app.environment}") private String environment;
    @Value("${grpc.client.GLOBAL.negotiationType}") private String clientNegotiationType;
    @Value("${app.internal-grpc-token}") private String internalGrpcToken;
    @Value("${spring.data.redis.password}") private String redisPassword;

    @PostConstruct
    void validateProductionSecurity() {
        ProductionTransportValidator.requireSecureGrpcInProd(environment, clientNegotiationType, false);
        ProductionSecretsValidator.requireSecret(environment, "INTERNAL_GRPC_TOKEN", internalGrpcToken);
        ProductionSecretsValidator.requireSecret(environment, "REDIS_PASSWORD", redisPassword);
    }

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor gatewayGrpcClientInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new GatewayGrpcClientInterceptor(token);
    }
}
