package com.serdar.community.client;

import com.serdar.proto.common.IdRequest;
import com.serdar.proto.user.BlockStatusRequest;
import com.serdar.proto.user.UserProfile;
import com.serdar.proto.user.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

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

    public boolean isBlockedEitherWay(long viewerId, long otherId) {
        return stub.isBlockedEitherWay(
                BlockStatusRequest.newBuilder().setCallerId(viewerId).setOtherId(otherId).build()
        ).getValue();
    }
}
