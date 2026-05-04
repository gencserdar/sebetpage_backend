package com.serdar.user.client;

import com.serdar.proto.auth.*;
import com.serdar.proto.common.StringRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over auth-service's gRPC stub. We proxy the handful of calls
 * user-service needs (uniqueness checks, credential mutations) rather than
 * passing the generated stub around directly.
 */
@Component
public class AuthClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    public boolean isEmailTaken(String email) {
        return stub.isEmailTaken(StringRequest.newBuilder().setValue(email).build()).getValue();
    }

    public boolean isNicknameTaken(String nickname) {
        return stub.isNicknameTaken(StringRequest.newBuilder().setValue(nickname).build()).getValue();
    }

    public void updateEmail(long userId, String newEmail) {
        stub.updateEmail(UpdateEmailRequest.newBuilder().setUserId(userId).setNewEmail(newEmail).build());
    }

    public void updateNickname(long userId, String newNickname) {
        stub.updateNickname(UpdateNicknameRequest.newBuilder().setUserId(userId).setNewNickname(newNickname).build());
    }
}
