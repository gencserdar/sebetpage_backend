package com.serdar.gateway.client;

import com.google.protobuf.ByteString;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.common.StringRequest;
import com.serdar.proto.user.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class UserClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub stub;

    public UserProfile createProfile(long id, String email, String nickname, String name, String surname) {
        return stub.createProfile(CreateProfileRequest.newBuilder()
                .setUserId(id).setEmail(email).setNickname(nickname)
                .setName(name).setSurname(surname).build());
    }

    public UserProfile byId(long id)    { return stub.getProfile(IdRequest.newBuilder().setId(id).build()); }
    public UserProfile byEmail(String e){ return stub.getProfileByEmail(StringRequest.newBuilder().setValue(e).build()); }
    public UserProfile byNickname(String n){ return stub.getProfileByNickname(StringRequest.newBuilder().setValue(n).build()); }

    public UserProfile updatePhoto(long id, byte[] bytes, String contentType, String filename) {
        return stub.updateProfilePhoto(UpdateProfilePhotoRequest.newBuilder()
                .setUserId(id).setImageBytes(ByteString.copyFrom(bytes))
                .setContentType(contentType == null ? "" : contentType)
                .setOriginalFilename(filename == null ? "" : filename)
                .build());
    }

    public String uploadImage(long uploaderId, byte[] bytes, String contentType, String filename) {
        return stub.uploadImage(UpdateProfilePhotoRequest.newBuilder()
                .setUserId(uploaderId).setImageBytes(ByteString.copyFrom(bytes))
                .setContentType(contentType == null ? "" : contentType)
                .setOriginalFilename(filename == null ? "" : filename)
                .build()).getValue();
    }

    public UserProfile updateNameSurname(long id, String name, String surname) {
        return stub.updateNameSurname(UpdateNameSurnameRequest.newBuilder()
                .setUserId(id).setName(name == null ? "" : name).setSurname(surname == null ? "" : surname).build());
    }

    public UserProfile changeNickname(long id, String nick) {
        return stub.changeNickname(ChangeNicknameRequest.newBuilder().setUserId(id).setNewNickname(nick).build());
    }

    public UserProfile changeEmail(long id, String email) {
        return stub.changeEmail(ChangeEmailRequest.newBuilder().setUserId(id).setNewEmail(email).build());
    }

    /** Sync the denormalized email column on the user-service profile after
     *  auth-service has applied a verified change. Skips the uniqueness check. */
    public UserProfile syncProfileEmail(long id, String email) {
        return stub.syncProfileEmail(ChangeEmailRequest.newBuilder().setUserId(id).setNewEmail(email).build());
    }

    public ProfileSettings getProfileSettings(long userId) {
        return stub.getProfileSettings(IdRequest.newBuilder().setId(userId).build());
    }

    public ProfileSettings updateProfileSettings(
            long userId, String bio, String socialLinksJson, String profileCardJson) {
        return stub.updateProfileSettings(UpdateProfileSettingsRequest.newBuilder()
                .setUserId(userId)
                .setBio(bio == null ? "" : bio)
                .setSocialLinksJson(socialLinksJson == null ? "[]" : socialLinksJson)
                .setProfileCardJson(profileCardJson == null ? "{\"widgets\":[]}" : profileCardJson)
                .build());
    }

    public void deleteUserData(long userId) {
        stub.deleteUserData(IdRequest.newBuilder().setId(userId).build());
    }

    public FriendStatusResponse friendStatus(long callerId, String otherNickname) {
        return stub.getFriendStatus(GetFriendStatusRequest.newBuilder()
                .setCallerId(callerId).setOtherNickname(otherNickname).build());
    }

    public UserList listFriends(long id) { return stub.listFriends(IdRequest.newBuilder().setId(id).build()); }

    /** Friend ids only — used by EntityEventBroadcaster to compute the
     *  audience for USER_UPDATED events without paying for full profile rows. */
    public java.util.List<Long> friendIds(long id) {
        return stub.listFriendIds(IdRequest.newBuilder().setId(id).build()).getIdsList();
    }

    public void removeFriend(long a, long b) {
        stub.removeFriend(RemoveFriendRequest.newBuilder().setUserA(a).setUserB(b).build());
    }

    public SendFriendRequestResponse sendRequest(long from, String toNickname) {
        return stub.sendFriendRequest(SendFriendRequestRequest.newBuilder()
                .setFromUserId(from).setToNickname(toNickname).build());
    }

    public FriendRequestList incoming(long id) { return stub.listIncoming(IdRequest.newBuilder().setId(id).build()); }
    public FriendRequestList outgoing(long id) { return stub.listOutgoing(IdRequest.newBuilder().setId(id).build()); }

    public void respondToRequest(long requestId, long responderId, boolean accept) {
        stub.respondToRequest(RespondToRequestRequest.newBuilder()
                .setRequestId(requestId).setResponderId(responderId).setAccept(accept).build());
    }

    public void cancelRequest(long requestId, long cancellerId) {
        stub.cancelRequest(CancelRequestRequest.newBuilder()
                .setRequestId(requestId).setCancellerId(cancellerId).build());
    }

    public void block(long a, long b)   { stub.block(BlockRequest.newBuilder().setBlockerId(a).setBlockedId(b).build()); }
    public void unblock(long a, long b) { stub.unblock(BlockRequest.newBuilder().setBlockerId(a).setBlockedId(b).build()); }
    public BlockList myBlocks(long id)  { return stub.myBlocks(IdRequest.newBuilder().setId(id).build()); }
    public BlockStatusResponse blockStatus(long a, long b) {
        return stub.blockStatus(BlockStatusRequest.newBuilder().setCallerId(a).setOtherId(b).build());
    }

    public UserSearchList searchUsers(long callerId, String keyword) {
        return stub.searchUsers(SearchRequest.newBuilder().setCallerId(callerId).setKeyword(keyword).build());
    }
}
