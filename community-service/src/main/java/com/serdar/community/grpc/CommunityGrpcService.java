package com.serdar.community.grpc;

import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import com.serdar.common.grpc.GatewayUserContext;
import com.serdar.community.entity.CommunityMember;
import com.serdar.community.service.CommunityDomainService;
import com.serdar.proto.common.BoolResponse;
import com.serdar.proto.common.Empty;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.community.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.ZoneOffset;

@GrpcService
@RequiredArgsConstructor
public class CommunityGrpcService extends CommunityServiceGrpc.CommunityServiceImplBase {

    private final CommunityDomainService svc;

    @Override
    public void createCommunity(CreateCommunityRequest req, StreamObserver<Community> out) {
        guard(out, () -> {
            com.serdar.community.entity.Community c =
                    svc.createCommunity(req.getCreatorId(), req.getName(), req.getDescription());
            out.onNext(toProto(c, CommunityMember.Role.ADMIN));
            out.onCompleted();
        });
    }

    @Override
    public void invite(InviteRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.invite(req.getCommunityId(), req.getInviterId(), req.getToUserId());
            out.onNext(Empty.getDefaultInstance());
            out.onCompleted();
        });
    }

    @Override
    public void respondToInvite(RespondInviteRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.respond(req.getInviteId(), req.getResponderId(), req.getAccept());
            out.onNext(Empty.getDefaultInstance());
            out.onCompleted();
        });
    }

    @Override
    public void myCommunities(IdRequest req, StreamObserver<CommunityList> out) {
        guard(out, () -> {
            CommunityList.Builder b = CommunityList.newBuilder();
            for (CommunityDomainService.CommunityWithRole cr : svc.myCommunities(req.getId())) {
                b.addCommunities(toProto(cr.community(), cr.role()));
            }
            out.onNext(b.build());
            out.onCompleted();
        });
    }

    @Override
    public void searchCommunities(SearchCommunitiesRequest req, StreamObserver<CommunityList> out) {
        guard(out, () -> {
            CommunityList.Builder b = CommunityList.newBuilder();
            svc.search(req.getKeyword()).forEach(c -> b.addCommunities(toProto(c, null)));
            out.onNext(b.build());
            out.onCompleted();
        });
    }

    @Override
    public void isMember(MembershipRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            Long viewerId = GatewayUserContext.currentViewerId();
            if (viewerId == null || viewerId != req.getUserId()) {
                throw ServiceException.forbidden("Cannot query membership for another user");
            }
            out.onNext(BoolResponse.newBuilder()
                    .setValue(svc.isMember(req.getCommunityId(), req.getUserId()))
                    .build());
            out.onCompleted();
        });
    }

    private static Community toProto(com.serdar.community.entity.Community c, CommunityMember.Role role) {
        Community.Builder b = Community.newBuilder()
                .setId(c.getId())
                .setName(c.getName() == null ? "" : c.getName())
                .setDescription(c.getDescription() == null ? "" : c.getDescription())
                .setIsPrivate(Boolean.TRUE.equals(c.getIsPrivate()))
                .setCreatedBy(c.getCreatedBy())
                .setCreatedAtMillis(c.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        if (role != null) {
            b.setMyRole(switch (role) {
                case ADMIN   -> CommunityRole.ADMIN;
                case MEMBER  -> CommunityRole.MEMBER;
                case PENDING -> CommunityRole.PENDING;
            });
        }
        return b.build();
    }

    private static void guard(StreamObserver<?> out, Runnable r) {
        try { r.run(); }
        catch (ServiceException e) { out.onError(GrpcErrors.toGrpc(e)); }
        catch (Exception e) { out.onError(GrpcErrors.toGrpc(
                new ServiceException(ServiceException.Code.INTERNAL, e.getMessage()))); }
    }
}
