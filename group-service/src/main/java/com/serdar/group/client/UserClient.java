package com.serdar.group.client;

import com.serdar.proto.common.IdRequest;
import com.serdar.proto.user.UserProfile;
import com.serdar.proto.user.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * Validates that a user id exists in user-service before we let it join a
 * group or receive an invite. Keeps FK-like integrity without a real FK.
 */
@Component
public class UserClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub stub;

    public UserProfile getProfile(long userId) {
        return stub.getProfile(IdRequest.newBuilder().setId(userId).build());
    }

    public boolean exists(long userId) {
        try { getProfile(userId); return true; }
        catch (Exception e) { return false; }
    }
}
