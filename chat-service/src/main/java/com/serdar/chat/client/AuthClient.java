package com.serdar.chat.client;

import com.serdar.proto.auth.AuthServiceGrpc;
import com.serdar.proto.common.IdRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class AuthClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    public boolean isFrozen(long userId) {
        return stub.getCredentialsById(IdRequest.newBuilder().setId(userId).build()).getFrozen();
    }
}
