package com.serdar.chat.client;

import com.serdar.proto.auth.AuthServiceGrpc;
import com.serdar.proto.common.IdRequest;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    /** Fail open when auth-service is temporarily unreachable (startup / restart). */
    public boolean isFrozen(long userId) {
        try {
            return stub.getCredentialsById(IdRequest.newBuilder().setId(userId).build()).getFrozen();
        } catch (Exception e) {
            log.warn("auth-service unavailable for isFrozen({}): {}", userId, e.getMessage());
            return false;
        }
    }
}
