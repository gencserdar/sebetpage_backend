package com.serdar.chat.client;

import com.serdar.proto.common.IdList;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.user.BlockStatusRequest;
import com.serdar.proto.user.UserProfile;
import com.serdar.proto.user.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UserClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub stub;

    public boolean isBlockedEitherWay(long a, long b) {
        return stub.isBlockedEitherWay(
                BlockStatusRequest.newBuilder().setCallerId(a).setOtherId(b).build()
        ).getValue();
    }

    public boolean blockedByMe(long me, long other) {
        return stub.blockStatus(
                BlockStatusRequest.newBuilder().setCallerId(me).setOtherId(other).build()
        ).getBlockedByMe();
    }

    public boolean blocksMe(long me, long other) {
        return stub.blockStatus(
                BlockStatusRequest.newBuilder().setCallerId(me).setOtherId(other).build()
        ).getBlocksMe();
    }

    public Set<Long> blockedByMeIds(long userId) {
        Set<Long> ids = new HashSet<>();
        stub.myBlocks(IdRequest.newBuilder().setId(userId).build())
                .getBlocksList()
                .forEach(b -> ids.add(b.getBlockedId()));
        return ids;
    }

    public List<Long> friendIds(long userId) {
        IdList r = stub.listFriendIds(IdRequest.newBuilder().setId(userId).build());
        return r.getIdsList();
    }

    public String nickname(long userId) {
        try {
            UserProfile p = stub.getProfile(IdRequest.newBuilder().setId(userId).build());
            return p.getNickname() == null || p.getNickname().isBlank()
                    ? "User " + userId
                    : p.getNickname();
        } catch (Exception ignored) {
            return "User " + userId;
        }
    }
}
