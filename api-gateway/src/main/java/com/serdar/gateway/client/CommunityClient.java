package com.serdar.gateway.client;

import com.serdar.proto.common.IdRequest;
import com.serdar.proto.community.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class CommunityClient {
    @GrpcClient("community-service")
    private CommunityServiceGrpc.CommunityServiceBlockingStub stub;

    public Community createCommunity(long creatorId, String name, String description) {
        return stub.createCommunity(CreateCommunityRequest.newBuilder()
                .setCreatorId(creatorId).setName(name).setDescription(description == null ? "" : description).build());
    }

    public void invite(long communityId, long inviter, long to) {
        stub.invite(InviteRequest.newBuilder()
                .setCommunityId(communityId).setInviterId(inviter).setToUserId(to).build());
    }

    public void respond(long inviteId, long responder, boolean accept) {
        stub.respondToInvite(RespondInviteRequest.newBuilder()
                .setInviteId(inviteId).setResponderId(responder).setAccept(accept).build());
    }

    public CommunityList mine(long userId) {
        return stub.myCommunities(IdRequest.newBuilder().setId(userId).build());
    }

    public CommunityList search(String kw) {
        return stub.searchCommunities(SearchCommunitiesRequest.newBuilder().setKeyword(kw).build());
    }

    public boolean isMember(long communityId, long userId) {
        return stub.isMember(MembershipRequest.newBuilder()
                .setCommunityId(communityId).setUserId(userId).build()).getValue();
    }
}
