package com.serdar.user.grpc;

import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import com.serdar.common.grpc.GatewayUserContext;
import com.serdar.proto.common.BoolResponse;
import com.serdar.proto.common.Empty;
import com.serdar.proto.common.IdList;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.common.StringRequest;
import com.serdar.proto.user.*;
import com.serdar.user.client.AuthClient;
import com.serdar.user.entity.FriendRequest;
import com.serdar.user.entity.UserBlock;
import com.serdar.user.entity.UserProfile;
import com.serdar.user.service.BlockService;
import com.serdar.user.service.FriendService;
import com.serdar.user.service.ProfileService;
import com.serdar.user.service.ProfileVisibilityService;
import com.serdar.user.service.SearchService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.ZoneOffset;
import java.util.Map;

@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final ProfileService profileService;
    private final FriendService friendService;
    private final BlockService blockService;
    private final SearchService searchService;
    private final AuthClient authClient;
    private final ProfileVisibilityService profileVisibility;

    // ---- profile -----------------------------------------------------------

    @Override
    public void createProfile(CreateProfileRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.createProfile(req.getUserId(), req.getEmail(), req.getNickname(),
                    req.getName(), req.getSurname());
            out.onNext(toProto(p)); out.onCompleted();
        });
    }

    @Override
    public void getProfile(IdRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.getById(req.getId());
            Long viewerId = GatewayUserContext.currentViewerId();
            profileVisibility.assertProfileAccessible(req.getId(), viewerId);
            if (profileVisibility.showFullProfile(req.getId(), viewerId)) {
                out.onNext(toProto(p));
            } else {
                out.onNext(frozenPublicProto(p));
            }
            out.onCompleted();
        });
    }

    @Override
    public void getProfileByEmail(StringRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.getByEmail(req.getValue());
            Long viewerId = GatewayUserContext.currentViewerId();
            profileVisibility.assertProfileAccessible(p.getId(), viewerId);
            if (profileVisibility.showFullProfile(p.getId(), viewerId)) {
                out.onNext(toProto(p));
            } else {
                out.onNext(frozenPublicProto(p));
            }
            out.onCompleted();
        });
    }

    @Override
    public void getProfileByNickname(StringRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.getByNickname(req.getValue());
            Long viewerId = GatewayUserContext.currentViewerId();
            profileVisibility.assertProfileAccessible(p.getId(), viewerId);
            if (profileVisibility.showFullProfile(p.getId(), viewerId)) {
                out.onNext(toProto(p));
            } else {
                out.onNext(frozenPublicProto(p));
            }
            out.onCompleted();
        });
    }

    @Override
    public void updateProfilePhoto(UpdateProfilePhotoRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.updatePhoto(req.getUserId(), req.getImageBytes().toByteArray(),
                    req.getContentType(), req.getOriginalFilename());
            out.onNext(toProto(p)); out.onCompleted();
        });
    }

    @Override
    public void uploadImage(UpdateProfilePhotoRequest req, StreamObserver<com.serdar.proto.common.StringRequest> out) {
        guard(out, () -> {
            String url = profileService.uploadImage(req.getUserId(), req.getImageBytes().toByteArray(),
                    req.getContentType(), req.getOriginalFilename());
            out.onNext(com.serdar.proto.common.StringRequest.newBuilder().setValue(url).build());
            out.onCompleted();
        });
    }

    @Override
    public void updateNameSurname(UpdateNameSurnameRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            UserProfile p = profileService.updateNameSurname(req.getUserId(), req.getName(), req.getSurname());
            out.onNext(toProto(p)); out.onCompleted();
        });
    }

    @Override
    public void changeNickname(ChangeNicknameRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            out.onNext(toProto(profileService.changeNickname(req.getUserId(), req.getNewNickname())));
            out.onCompleted();
        });
    }

    @Override
    public void changeEmail(ChangeEmailRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            throw ServiceException.invalid("Direct email change is disabled; use confirm-email-change flow");
        });
    }

    @Override
    public void syncProfileEmail(ChangeEmailRequest req, StreamObserver<com.serdar.proto.user.UserProfile> out) {
        guard(out, () -> {
            out.onNext(toProto(profileService.syncProfileEmail(req.getUserId(), req.getNewEmail())));
            out.onCompleted();
        });
    }

    @Override
    public void getProfileSettings(IdRequest req, StreamObserver<ProfileSettings> out) {
        guard(out, () -> {
            Long viewerId = GatewayUserContext.currentViewerId();
            profileVisibility.assertProfileAccessible(req.getId(), viewerId);
            if (!profileVisibility.showFullProfile(req.getId(), viewerId)) {
                out.onNext(emptySettings(req.getId()));
                out.onCompleted();
                return;
            }
            out.onNext(toSettingsProto(profileService.getProfileSettings(req.getId())));
            out.onCompleted();
        });
    }

    @Override
    public void updateProfileSettings(UpdateProfileSettingsRequest req, StreamObserver<ProfileSettings> out) {
        guard(out, () -> {
            UserProfile p = profileService.updateProfileSettings(
                    req.getUserId(),
                    req.getBio(),
                    req.getSocialLinksJson(),
                    req.getProfileCardJson());
            out.onNext(toSettingsProto(p));
            out.onCompleted();
        });
    }

    @Override
    public void deleteUserData(IdRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            profileService.deleteUserData(req.getId());
            out.onNext(Empty.getDefaultInstance());
            out.onCompleted();
        });
    }

    // ---- friendships -------------------------------------------------------

    @Override
    public void getFriendStatus(GetFriendStatusRequest req, StreamObserver<FriendStatusResponse> out) {
        guard(out, () -> {
            Map<String, Object> m = friendService.friendStatus(req.getCallerId(), req.getOtherNickname());
            FriendStatusResponse.Builder b = FriendStatusResponse.newBuilder()
                    .setStatus((String) m.get("status"));
            if (m.get("requestId") instanceof Long rid) b.setRequestId(rid);
            if (m.get("otherUserId") instanceof Long oid) b.setOtherUserId(oid);
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void listFriends(IdRequest req, StreamObserver<UserList> out) {
        guard(out, () -> {
            UserList.Builder b = UserList.newBuilder();
            friendService.listFriends(req.getId()).forEach(p -> b.addUsers(toProto(p)));
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void listFriendIds(IdRequest req, StreamObserver<IdList> out) {
        guard(out, () -> {
            IdList.Builder b = IdList.newBuilder();
            friendService.listFriendIds(req.getId()).forEach(b::addIds);
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void removeFriend(RemoveFriendRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            friendService.removeFriend(req.getUserA(), req.getUserB());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void sendFriendRequest(SendFriendRequestRequest req, StreamObserver<SendFriendRequestResponse> out) {
        guard(out, () -> {
            Map<String, Object> m = friendService.sendRequest(req.getFromUserId(), req.getToNickname());
            SendFriendRequestResponse.Builder b = SendFriendRequestResponse.newBuilder()
                    .setStatus((String) m.get("status"));
            if (m.get("requestId") instanceof Long rid) b.setRequestId(rid);
            if (m.get("toUserId") instanceof Long tid)  b.setToUserId(tid);
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void listIncoming(IdRequest req, StreamObserver<FriendRequestList> out) {
        guard(out, () -> {
            FriendRequestList.Builder b = FriendRequestList.newBuilder();
            friendService.incoming(req.getId()).forEach(r -> b.addRequests(toProto(r)));
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void listOutgoing(IdRequest req, StreamObserver<FriendRequestList> out) {
        guard(out, () -> {
            FriendRequestList.Builder b = FriendRequestList.newBuilder();
            friendService.outgoing(req.getId()).forEach(r -> b.addRequests(toProto(r)));
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void respondToRequest(RespondToRequestRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            friendService.respondToRequest(req.getRequestId(), req.getResponderId(), req.getAccept());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void cancelRequest(CancelRequestRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            friendService.cancelRequest(req.getRequestId(), req.getCancellerId());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    // ---- blocks ------------------------------------------------------------

    @Override
    public void block(BlockRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            blockService.block(req.getBlockerId(), req.getBlockedId());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void unblock(BlockRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            blockService.unblock(req.getBlockerId(), req.getBlockedId());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void myBlocks(IdRequest req, StreamObserver<BlockList> out) {
        guard(out, () -> {
            BlockList.Builder b = BlockList.newBuilder();
            for (UserBlock block : blockService.myBlocks(req.getId())) {
                if (authClient.isFrozen(block.getBlockedId())) continue;
                UserProfile target = profileService.getById(block.getBlockedId());
                b.addBlocks(Block.newBuilder()
                        .setId(block.getId())
                        .setBlockerId(block.getBlockerId())
                        .setBlockedId(block.getBlockedId())
                        .setBlockedNickname(ns(target.getNickname()))
                        .setBlockedProfileImageUrl(ns(target.getProfileImageUrl()))
                        .setCreatedAtMillis(block.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                        .build());
            }
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void blockStatus(BlockStatusRequest req, StreamObserver<BlockStatusResponse> out) {
        boolean me = blockService.blockedByMe(req.getCallerId(), req.getOtherId());
        boolean other = blockService.blocksMe(req.getCallerId(), req.getOtherId());
        out.onNext(BlockStatusResponse.newBuilder()
                .setBlockedByMe(me).setBlocksMe(other).setEither(me || other).build());
        out.onCompleted();
    }

    @Override
    public void isBlockedEitherWay(BlockStatusRequest req, StreamObserver<BoolResponse> out) {
        out.onNext(BoolResponse.newBuilder()
                .setValue(blockService.eitherWay(req.getCallerId(), req.getOtherId())).build());
        out.onCompleted();
    }

    // ---- search ------------------------------------------------------------

    @Override
    public void searchUsers(SearchRequest req, StreamObserver<UserSearchList> out) {
        guard(out, () -> {
            UserSearchList.Builder b = UserSearchList.newBuilder();
            for (SearchService.UserSearchResult r : searchService.searchUsers(req.getCallerId(), req.getKeyword())) {
                UserProfile p = r.profile();
                b.addUsers(UserSummary.newBuilder()
                        .setId(p.getId())
                        .setNickname(ns(p.getNickname()))
                        .setName(ns(p.getName()))
                        .setSurname(ns(p.getSurname()))
                        .setProfileImageUrl(ns(p.getProfileImageUrl()))
                        .setMutualFriendCount(r.mutualCount())
                        .build());
            }
            out.onNext(b.build()); out.onCompleted();
        });
    }

    // ---- conversion helpers ------------------------------------------------

    private static com.serdar.proto.user.UserProfile frozenPublicProto(UserProfile p) {
        return com.serdar.proto.user.UserProfile.newBuilder()
                .setId(p.getId())
                .setNickname(ns(p.getNickname()))
                .build();
    }

    private static ProfileSettings emptySettings(long userId) {
        return ProfileSettings.newBuilder()
                .setUserId(userId)
                .setBio("")
                .setSocialLinksJson("[]")
                .setProfileCardJson("{\"widgets\":[]}")
                .build();
    }

    private static com.serdar.proto.user.UserProfile toProto(UserProfile p) {
        return com.serdar.proto.user.UserProfile.newBuilder()
                .setId(p.getId())
                .setEmail(ns(p.getEmail()))
                .setNickname(ns(p.getNickname()))
                .setName(ns(p.getName()))
                .setSurname(ns(p.getSurname()))
                .setProfileImageUrl(ns(p.getProfileImageUrl()))
                .setActivated(true)
                .setRole("USER")
                .build();
    }

    private static ProfileSettings toSettingsProto(UserProfile p) {
        return ProfileSettings.newBuilder()
                .setUserId(p.getId())
                .setBio(ns(p.getBio()))
                .setSocialLinksJson(blankToDefault(p.getSocialLinksJson(), "[]"))
                .setProfileCardJson(blankToDefault(p.getProfileCardJson(), "{\"widgets\":[]}"))
                .build();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private com.serdar.proto.user.FriendRequest toProto(FriendRequest r) {
        UserProfile from = profileService.getById(r.getFromUserId());
        UserProfile to   = profileService.getById(r.getToUserId());
        return com.serdar.proto.user.FriendRequest.newBuilder()
                .setId(r.getId())
                .setFromUserId(r.getFromUserId())
                .setToUserId(r.getToUserId())
                .setStatus(r.getStatus().name())
                .setSentAtMillis(r.getSentAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                .setFromUser(toProto(from))
                .setToUser(toProto(to))
                .build();
    }

    private static String ns(String s) { return s == null ? "" : s; }

    private static void guard(StreamObserver<?> out, Runnable r) {
        try { r.run(); }
        catch (ServiceException e) { out.onError(GrpcErrors.toGrpc(e)); }
        catch (Exception e) { out.onError(GrpcErrors.toGrpc(
                new ServiceException(ServiceException.Code.INTERNAL, e.getMessage()))); }
    }
}
