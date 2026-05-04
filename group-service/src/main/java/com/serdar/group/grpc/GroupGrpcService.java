package com.serdar.group.grpc;

import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import com.serdar.group.entity.GroupMember;
import com.serdar.group.service.GroupDomainService;
import com.serdar.proto.common.BoolResponse;
import com.serdar.proto.common.Empty;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.group.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.ZoneOffset;

@GrpcService
@RequiredArgsConstructor
public class GroupGrpcService extends GroupServiceGrpc.GroupServiceImplBase {

    private final GroupDomainService svc;

    @Override
    public void createGroup(CreateGroupRequest req, StreamObserver<com.serdar.proto.group.Group> out) {
        guard(out, () -> {
            com.serdar.group.entity.Group g = svc.createGroup(req.getCreatorId(), req.getName(), req.getDescription());
            out.onNext(toProto(g, GroupMember.Role.ADMIN)); out.onCompleted();
        });
    }

    @Override
    public void invite(InviteRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.invite(req.getGroupId(), req.getInviterId(), req.getToUserId());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void respondToInvite(RespondInviteRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.respond(req.getInviteId(), req.getResponderId(), req.getAccept());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void myGroups(IdRequest req, StreamObserver<GroupList> out) {
        guard(out, () -> {
            GroupList.Builder b = GroupList.newBuilder();
            for (GroupDomainService.GroupWithRole gr : svc.myGroups(req.getId())) {
                b.addGroups(toProto(gr.group(), gr.role()));
            }
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void searchGroups(SearchGroupsRequest req, StreamObserver<GroupList> out) {
        guard(out, () -> {
            GroupList.Builder b = GroupList.newBuilder();
            svc.search(req.getKeyword()).forEach(g -> b.addGroups(toProto(g, null)));
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void isMember(MembershipRequest req, StreamObserver<BoolResponse> out) {
        out.onNext(BoolResponse.newBuilder().setValue(svc.isMember(req.getGroupId(), req.getUserId())).build());
        out.onCompleted();
    }

    private static com.serdar.proto.group.Group toProto(com.serdar.group.entity.Group g, GroupMember.Role role) {
        com.serdar.proto.group.Group.Builder b = com.serdar.proto.group.Group.newBuilder()
                .setId(g.getId())
                .setName(g.getName() == null ? "" : g.getName())
                .setDescription(g.getDescription() == null ? "" : g.getDescription())
                .setIsPrivate(Boolean.TRUE.equals(g.getIsPrivate()))
                .setCreatedBy(g.getCreatedBy())
                .setCreatedAtMillis(g.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
        if (role != null) {
            b.setMyRole(switch (role) {
                case ADMIN   -> GroupRole.ADMIN;
                case MEMBER  -> GroupRole.MEMBER;
                case PENDING -> GroupRole.PENDING;
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
