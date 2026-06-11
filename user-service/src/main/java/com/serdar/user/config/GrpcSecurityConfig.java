package com.serdar.user.config;

import com.serdar.common.grpc.InternalGrpcClientInterceptor;
import com.serdar.common.config.ProductionTransportValidator;
import com.serdar.common.grpc.InternalGrpcActorServerInterceptor;
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

    @PostConstruct
    void validateProductionTransport() {
        ProductionTransportValidator.requireSecureGrpcInProd(
                environment, clientNegotiationType, serverSecurityEnabled);
    }

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor internalGrpcAuthClientInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new InternalGrpcClientInterceptor(token);
    }

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor internalGrpcAuthServerInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new InternalGrpcServerInterceptor(token);
    }

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor internalGrpcActorServerInterceptor() {
        return new InternalGrpcActorServerInterceptor();
    }
}
