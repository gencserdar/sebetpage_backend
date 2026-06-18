package com.serdar.gateway.ws;

import com.serdar.common.grpc.GrpcActorContext;
import com.serdar.gateway.client.ChatClient;
import com.serdar.proto.chat.ChatEvent;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges chat-service gRPC streaming events onto the gateway's STOMP broker.
 *
 * Destinations and payload shapes are the contract the frontend was built
 * against in the monolith:
 *
 *   /user/queue/friends              — presence snapshot, presence updates,
 *                                       friend request / friendship events
 *   /user/queue/messages/{convId}    — real-time messages + READ receipts
 *   /user/queue/unread               — total / per-conversation unread updates
 *
 * Per STOMP session we open one SubscribeEvents RPC to chat-service; when the
 * STOMP session disconnects we tear the gRPC call down so the server's
 * onCancelHandler fires promptly.
 *
 * Snapshot caching: chat-service pushes a PRESENCE_SNAPSHOT immediately when
 * the gRPC subscription opens, which can race ahead of the client's STOMP
 * subscribe. We cache the most recent snapshot per user so the frontend's
 * /app/friends/snapshot publish can replay it once the client is ready.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsBridgeService {

    private final ChatClient chat;
    private final SimpMessagingTemplate stomp;

    // sessionId -> cancellable gRPC context so we can tear the server stream down on disconnect.
    private final Map<String, Context.CancellableContext> sessions = new ConcurrentHashMap<>();

    // userId -> last presence snapshot payload (already in the JSON shape we send to STOMP).
    // Replayed on demand when the frontend publishes /app/friends/snapshot — closes the
    // race between chat-service's eager snapshot and the client's subscribe.
    private final Map<Long, Map<String, Object>> lastSnapshot = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        var acc = org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = acc.getSessionId();
        if (acc.getUser() == null || sessionId == null) return;
        long userId = Long.parseLong(acc.getUser().getName());

        StreamObserver<ChatEvent> observer = new StreamObserver<>() {
            @Override public void onNext(ChatEvent e)       { dispatch(userId, e); }
            @Override public void onError(Throwable t)      { log.warn("Chat stream error for user {}: {}", userId, t.getMessage()); }
            @Override public void onCompleted()             { log.debug("Chat stream completed for user {}", userId); }
        };
        Context.CancellableContext ctx = chat.subscribeEvents(userId, observer);
        sessions.put(sessionId, ctx);
        log.debug("WS bridge subscribed user {} (session {})", userId, sessionId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Context.CancellableContext ctx = sessions.remove(sessionId);
        if (ctx != null) {
            ctx.cancel(null);
            log.debug("WS bridge unsubscribed session {}", sessionId);
        }
        // Remove cached presence snapshot so disconnected users don't accumulate
        // entries indefinitely in the lastSnapshot map. The principal name is the
        // userId string set by StompPrincipalHandshakeHandler during the WS handshake.
        if (event.getUser() != null) {
            try {
                long userId = Long.parseLong(event.getUser().getName());
                lastSnapshot.remove(userId);
                log.debug("Cleared lastSnapshot for user {} (session {})", userId, sessionId);
            } catch (NumberFormatException ignored) {}
        }
    }

    /** Push a fresh presence snapshot (includes newly added friends). */
    public void replaySnapshot(long userId) {
        try {
            ChatEvent snapshot = GrpcActorContext.callAs(userId, () -> chat.getPresenceSnapshot(userId));
            dispatch(userId, snapshot);
        } catch (Exception e) {
            log.warn("Failed to refresh presence snapshot for user {}: {}", userId, e.getMessage());
            Map<String, Object> snap = lastSnapshot.get(userId);
            if (snap != null) {
                stomp.convertAndSendToUser(String.valueOf(userId), "/queue/friends", snap);
            }
        }
    }

    /** Route a ChatEvent to the right user-scoped STOMP destination. */
    private void dispatch(long userId, ChatEvent e) {
        String user = String.valueOf(userId);
        switch (e.getType()) {
            case "MESSAGE" -> {
                // Per-conversation queue so the frontend's dynamic
                // subscribeToConversation(conversationId, cb) lines up.
                // Body is the WsMessageDTO shape the UI casts to directly.
                long convId = e.getConversationId();
                Map<String, Object> body = messageBody(e.getMessage());
                stomp.convertAndSendToUser(user, "/queue/messages/" + convId, body);
            }
            case "MESSAGE_DELETED", "MESSAGE_EDITED" -> {
                long convId = e.getConversationId();
                Map<String, Object> body = messageBody(e.getMessage());
                body.put("type", e.getType());
                stomp.convertAndSendToUser(user, "/queue/messages/" + convId, body);
            }
            case "TYPING" -> {
                long convId = e.getConversationId();
                stomp.convertAndSendToUser(user, "/queue/messages/" + convId, Map.of(
                        "type", "TYPING",
                        "conversationId", convId,
                        "userId", e.getSubjectUserId()
                ));
            }
            case "READ" -> {
                // Frontend's per-conversation handler branches on `type === "READ"`
                // and reads `readerUserId` + `lastReadAt`.
                long convId = e.getConversationId();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", "READ");
                body.put("conversationId", convId);
                body.put("readerUserId", e.getReaderId());
                body.put("lastReadAt", Instant.ofEpochMilli(e.getLastReadAtMillis()).toString());
                stomp.convertAndSendToUser(user, "/queue/messages/" + convId, body);
            }
            case "PRESENCE_UPDATE" -> stomp.convertAndSendToUser(user, "/queue/friends", Map.of(
                    "type", "PRESENCE_UPDATE",
                    "userId", e.getSubjectUserId(),
                    "online", e.getOnline()
            ));
            case "PRESENCE_SNAPSHOT" -> {
                List<Map<String, Object>> users = e.getPresenceSnapshotList().stream()
                        .map(p -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("userId", p.getUserId());
                            m.put("online", p.getOnline());
                            return m;
                        })
                        .toList();
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", "PRESENCE_SNAPSHOT");
                body.put("users", users);
                lastSnapshot.put(userId, body);
                stomp.convertAndSendToUser(user, "/queue/friends", body);
            }
            case "UNREAD_COUNT_UPDATE" -> stomp.convertAndSendToUser(user, "/queue/unread", Map.of(
                    "conversationId", e.getConversationId(),
                    "unreadCount", e.getUnreadCount(),
                    "totalUnreadCount", e.getTotalUnreadCount()
            ));
            case "MESSAGING_GROUP_ADDED", "MESSAGING_GROUP_UPDATED", "MESSAGING_GROUP_LEFT", "MESSAGING_GROUP_DELETED" -> stomp.convertAndSendToUser(user, "/queue/friends", Map.of(
                    "type", e.getType(),
                    "conversationId", e.getConversationId()
            ));
            default -> log.debug("Unhandled chat event type: {}", e.getType());
        }
    }

    private static Map<String, Object> messageBody(com.serdar.proto.chat.ChatMessage m) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", Long.toString(m.getId()));
        body.put("conversationId", m.getConversationId());
        body.put("senderId", m.getSenderId());
        body.put("content", m.getContent());
        body.put("createdAt", Instant.ofEpochMilli(m.getCreatedAtMillis()).toString());
        body.put("createdAtMillis", m.getCreatedAtMillis());
        if (m.getEditedAtMillis() > 0) {
            body.put("editedAt", Instant.ofEpochMilli(m.getEditedAtMillis()).toString());
        }
        body.put("deleted", m.getDeleted());
        return body;
    }
}
