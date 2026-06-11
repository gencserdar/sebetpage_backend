package com.serdar.community.config;

import com.serdar.common.grpc.InternalGrpcClientInterceptor;
import com.serdar.common.grpc.InternalGrpcServerInterceptor;
import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
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

    @Bean
    @GrpcGlobalServerInterceptor
    ServerInterceptor internalGrpcAuthServerInterceptor(@Value("${app.internal-grpc-token}") String token) {
        return new InternalGrpcServerInterceptor(token);
    }
}
