package com.serdar.gateway.config;

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

    @PostConstruct
    void validateProductionTransport() {
        ProductionTransportValidator.requireSecureGrpcInProd(environment, clientNegotiationType, false);
    }

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor gatewayGrpcClientInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new GatewayGrpcClientInterceptor(token);
    }
}
