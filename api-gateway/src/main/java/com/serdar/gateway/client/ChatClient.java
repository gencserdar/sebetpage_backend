package com.serdar.gateway.client;

import com.serdar.proto.chat.*;
import com.serdar.proto.common.IdRequest;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class ChatClient {
    @GrpcClient("chat-service") private ChatServiceGrpc.ChatServiceBlockingStub blocking;
    @GrpcClient("chat-service") private ChatServiceGrpc.ChatServiceStub async;

    public Conversation getOrCreateDirect(long a, long b) {
        return blocking.getOrCreateDirect(GetOrCreateDirectRequest.newBuilder()
                .setUserAId(a).setUserBId(b).build());
    }
    public ChatMessage send(long conversationId, long senderId, String content) {
        return blocking.sendMessage(SendMessageRequest.newBuilder()
                .setConversationId(conversationId).setSenderId(senderId).setPlaintext(content).build());
    }
    public MessagePage getPage(long conversationId, long callerId, int page, int size) {
        return blocking.getPage(GetPageRequest.newBuilder()
                .setConversationId(conversationId).setCallerId(callerId).setPage(page).setSize(size).build());
    }
    public MessageList getLatest(long conversationId, long callerId, int limit) {
        return blocking.getLatest(GetLatestRequest.newBuilder()
                .setConversationId(conversationId).setCallerId(callerId).setLimit(limit).build());
    }
    public MarkReadResponse markRead(long conversationId, long readerId) {
        return blocking.markRead(MarkReadRequest.newBuilder()
                .setConversationId(conversationId).setReaderId(readerId).build());
    }
    public ReadStateResponse readState(long conversationId, long callerId) {
        return blocking.getReadState(ReadStateRequest.newBuilder()
                .setConversationId(conversationId).setCallerId(callerId).build());
    }
    public UnreadCountsResponse unreadCounts(long userId) {
        return blocking.getUnreadCounts(IdRequest.newBuilder().setId(userId).build());
    }
    public ConversationList myConversations(long userId) {
        return blocking.myConversations(IdRequest.newBuilder().setId(userId).build());
    }

    public Conversation createMessagingGroup(long creatorId, java.util.List<Long> memberIds, String name) {
        return blocking.createMessagingGroup(CreateMessagingGroupRequest.newBuilder()
                .setCreatorId(creatorId)
                .addAllMemberIds(memberIds)
                .setName(name != null ? name : "")
                .build());
    }

    public Conversation addMessagingGroupMember(long conversationId, long requesterId, long newUserId) {
        return blocking.addMessagingGroupMember(AddMessagingGroupMemberRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .setNewUserId(newUserId)
                .build());
    }

    public MessagingGroupDetail removeMessagingGroupMember(long conversationId, long requesterId, long targetUserId) {
        return blocking.removeMessagingGroupMember(RemoveMessagingGroupMemberRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .setTargetUserId(targetUserId)
                .build());
    }

    public MessagingGroupDetail messagingGroupDetail(long conversationId, long requesterId) {
        return blocking.getMessagingGroupDetail(MessagingGroupDetailRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .build());
    }

    public MessagingGroupDetail updateMessagingGroup(
            long conversationId,
            long requesterId,
            boolean updateTitle,
            String title,
            boolean updateDescription,
            String description,
            boolean updateImageUrl,
            String imageUrl
    ) {
        return blocking.updateMessagingGroup(UpdateMessagingGroupRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .setUpdateTitle(updateTitle)
                .setTitle(title == null ? "" : title)
                .setUpdateDescription(updateDescription)
                .setDescription(description == null ? "" : description)
                .setUpdateImageUrl(updateImageUrl)
                .setImageUrl(imageUrl == null ? "" : imageUrl)
                .build());
    }

    public MessagingGroupDetail updateMessagingGroupParticipant(
            long conversationId,
            long requesterId,
            long targetUserId,
            boolean updateMuted,
            boolean muted,
            boolean updatePermissions,
            MessagingGroupPermissionSet permissions
    ) {
        return blocking.updateMessagingGroupParticipant(UpdateMessagingGroupParticipantRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .setTargetUserId(targetUserId)
                .setUpdateMuted(updateMuted)
                .setMuted(muted)
                .setUpdatePermissions(updatePermissions)
                .setPermissions(permissions == null ? MessagingGroupPermissionSet.getDefaultInstance() : permissions)
                .build());
    }

    public void exitMessagingGroup(long conversationId, long requesterId) {
        blocking.exitMessagingGroup(MessagingGroupActionRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .build());
    }

    public void deleteMessagingGroup(long conversationId, long requesterId) {
        blocking.deleteMessagingGroup(MessagingGroupActionRequest.newBuilder()
                .setConversationId(conversationId)
                .setRequesterId(requesterId)
                .build());
    }

    /**
     * Async subscription used by the WebSocket bridge. Returns a cancellable
     * context; calling {@code cancel(null)} on it tears the gRPC stream down
     * cleanly so the server's onCancelHandler fires immediately (instead of
     * waiting for GC).
     */
    public ChatEvent getPresenceSnapshot(long userId) {
        return blocking.getPresenceSnapshot(IdRequest.newBuilder().setId(userId).build());
    }

    public Context.CancellableContext subscribeEvents(long userId, StreamObserver<ChatEvent> observer) {
        Context.CancellableContext ctx = Context.current().withCancellation();
        ctx.run(() -> async.subscribeEvents(IdRequest.newBuilder().setId(userId).build(), observer));
        return ctx;
    }
}
