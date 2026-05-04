package com.serdar.gateway.client;

import com.serdar.proto.common.IdRequest;
import com.serdar.proto.group.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class GroupClient {
    @GrpcClient("group-service")
    private GroupServiceGrpc.GroupServiceBlockingStub stub;

    public Group createGroup(long creatorId, String name, String description) {
        return stub.createGroup(CreateGroupRequest.newBuilder()
                .setCreatorId(creatorId).setName(name).setDescription(description == null ? "" : description).build());
    }
    public void invite(long groupId, long inviter, long to) {
        stub.invite(InviteRequest.newBuilder().setGroupId(groupId).setInviterId(inviter).setToUserId(to).build());
    }
    public void respond(long inviteId, long responder, boolean accept) {
        stub.respondToInvite(RespondInviteRequest.newBuilder()
                .setInviteId(inviteId).setResponderId(responder).setAccept(accept).build());
    }
    public GroupList mine(long userId) { return stub.myGroups(IdRequest.newBuilder().setId(userId).build()); }
    public GroupList search(String kw) { return stub.searchGroups(SearchGroupsRequest.newBuilder().setKeyword(kw).build()); }
    public boolean isMember(long groupId, long userId) {
        return stub.isMember(MembershipRequest.newBuilder().setGroupId(groupId).setUserId(userId).build()).getValue();
    }
}
