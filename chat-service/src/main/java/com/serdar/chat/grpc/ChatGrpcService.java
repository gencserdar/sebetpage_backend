package com.serdar.chat.grpc;

import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.Message;
import com.serdar.chat.service.ChatDomainService;
import com.serdar.chat.service.ConversationService;
import com.serdar.chat.service.EventBroker;
import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import com.serdar.proto.chat.*;
import com.serdar.proto.common.IdRequest;
import io.grpc.Context;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.ZoneOffset;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final ChatDomainService chat;
    private final ConversationService convs;
    private final EventBroker broker;

    @Override
    public void getOrCreateDirect(GetOrCreateDirectRequest req, StreamObserver<com.serdar.proto.chat.Conversation> out) {
        guard(out, () -> {
            Conversation c = convs.getOrCreateDirect(req.getUserAId(), req.getUserBId());
            out.onNext(toProto(c)); out.onCompleted();
        });
    }

    @Override
    public void sendMessage(SendMessageRequest req, StreamObserver<ChatMessage> out) {
        guard(out, () -> {
            Message m = chat.send(req.getConversationId(), req.getSenderId(), req.getPlaintext());
            out.onNext(ChatMessage.newBuilder()
                    .setId(m.getId())
                    .setConversationId(m.getConversationId())
                    .setSenderId(m.getSenderId())
                    .setContent(req.getPlaintext())
                    .setCreatedAtMillis(m.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void getPage(GetPageRequest req, StreamObserver<MessagePage> out) {
        guard(out, () -> {
            var page = chat.getPage(req.getConversationId(), req.getCallerId(), req.getPage(), req.getSize());
            MessagePage.Builder b = MessagePage.newBuilder()
                    .setPage(req.getPage()).setSize(req.getSize()).setTotal(page.getTotalElements());
            page.getContent().forEach(b::addMessages);
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void getLatest(GetLatestRequest req, StreamObserver<MessageList> out) {
        guard(out, () -> {
            MessageList.Builder b = MessageList.newBuilder();
            chat.getLatest(req.getConversationId(), req.getCallerId(), req.getLimit()).forEach(b::addMessages);
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void markRead(MarkReadRequest req, StreamObserver<MarkReadResponse> out) {
        guard(out, () -> {
            var r = chat.markRead(req.getConversationId(), req.getReaderId());
            out.onNext(MarkReadResponse.newBuilder()
                    .setConversationId(req.getConversationId())
                    .setUnreadCount(r.unread())
                    .setTotalUnreadCount(r.totalUnread())
                    .setLastReadAtMillis(r.lastReadAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void getReadState(ReadStateRequest req, StreamObserver<ReadStateResponse> out) {
        guard(out, () -> {
            var r = chat.readState(req.getConversationId(), req.getCallerId());
            ReadStateResponse.Builder b = ReadStateResponse.newBuilder()
                    .setFriendUserId(r.friendUserId())
                    .setMyUserId(r.myUserId());
            if (r.myLastReadAt() != null)
                b.setMyLastReadAtMillis(r.myLastReadAt().toInstant(ZoneOffset.UTC).toEpochMilli());
            if (r.friendLastReadAt() != null)
                b.setFriendLastReadAtMillis(r.friendLastReadAt().toInstant(ZoneOffset.UTC).toEpochMilli());
            if (r.seenMyMessageId() != null) b.setSeenMyMessageId(r.seenMyMessageId());
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void getUnreadCounts(IdRequest req, StreamObserver<UnreadCountsResponse> out) {
        guard(out, () -> {
            var r = chat.unreadCounts(req.getId());
            out.onNext(UnreadCountsResponse.newBuilder()
                    .setTotalCount(r.total())
                    .putAllPerConversation(r.perConversation())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void myConversations(IdRequest req, StreamObserver<ConversationList> out) {
        guard(out, () -> {
            ConversationList.Builder b = ConversationList.newBuilder();
            chat.myConversations(req.getId()).forEach(c -> b.addConversations(toProto(c)));
            out.onNext(b.build()); out.onCompleted();
        });
    }

    @Override
    public void createMessagingGroup(CreateMessagingGroupRequest req, StreamObserver<com.serdar.proto.chat.Conversation> out) {
        guard(out, () -> {
            Conversation c = convs.createMessagingGroup(
                    req.getCreatorId(), req.getMemberIdsList(), req.getName());
            out.onNext(toProto(c));
            out.onCompleted();
        });
    }

    @Override
    public void addMessagingGroupMember(AddMessagingGroupMemberRequest req, StreamObserver<com.serdar.proto.chat.Conversation> out) {
        guard(out, () -> {
            Conversation c = chat.addMessagingGroupMember(
                    req.getConversationId(), req.getRequesterId(), req.getNewUserId());
            out.onNext(toProto(c));
            out.onCompleted();
        });
    }

    @Override
    public void subscribeEvents(IdRequest req, StreamObserver<ChatEvent> out) {
        long userId = req.getId();
        broker.subscribe(userId, out);

        // Push a presence snapshot immediately so the client can render the friend list state.
        try { out.onNext(chat.presenceSnapshotFor(userId)); } catch (Exception ignore) { /* stream closed */ }
        chat.broadcastPresence(userId, true);

        if (out instanceof ServerCallStreamObserver<ChatEvent> srv) {
            srv.setOnCancelHandler(() -> {
                broker.unsubscribe(userId, out);
                // The RPC's Context is already cancelled by the time this
                // handler runs, so any downstream gRPC call made here would
                // inherit that cancellation and fail with CANCELLED. Fork to
                // a detached context so the offline-presence broadcast gets
                // to complete. Swallow anything that still slips through — we
                // don't want noisy stack traces on orderly disconnects.
                Context forked = Context.ROOT.fork();
                Context previous = forked.attach();
                try {
                    chat.broadcastPresence(userId, false);
                } catch (Exception e) {
                    log.debug("offline-presence broadcast suppressed for user {}: {}", userId, e.toString());
                } finally {
                    forked.detach(previous);
                }
            });
        }
    }

    // -- helpers -------------------------------------------------------------

    private static com.serdar.proto.chat.Conversation toProto(Conversation c) {
        return com.serdar.proto.chat.Conversation.newBuilder()
                .setId(c.getId())
                .setType(switch (c.getType()) {
                    case DIRECT          -> ConversationType.DIRECT;
                    case GROUP           -> ConversationType.GROUP;
                    case MESSAGING_GROUP -> ConversationType.MESSAGING_GROUP;
                })
                .setUserAId(c.getUserAId() == null ? 0 : c.getUserAId())
                .setUserBId(c.getUserBId() == null ? 0 : c.getUserBId())
                .setTitle(c.getTitle() == null ? "" : c.getTitle())
                .setCreatedAtMillis(c.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli())
                .build();
    }

    private static void guard(StreamObserver<?> out, Runnable r) {
        try { r.run(); }
        catch (ServiceException e) { out.onError(GrpcErrors.toGrpc(e)); }
        catch (Exception e) { out.onError(GrpcErrors.toGrpc(
                new ServiceException(ServiceException.Code.INTERNAL, e.getMessage()))); }
    }
}
