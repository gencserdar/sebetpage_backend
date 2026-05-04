package com.serdar.gateway.config;

import com.serdar.common.grpc.InternalGrpcClientInterceptor;
import io.grpc.ClientInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcSecurityConfig {

    @Bean
    @GrpcGlobalClientInterceptor
    ClientInterceptor internalGrpcAuthClientInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new InternalGrpcClientInterceptor(token);
    }
}
