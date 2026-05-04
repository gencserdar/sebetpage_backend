package com.serdar.gateway.ws;

import com.serdar.gateway.client.ChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP @MessageMapping handlers for client publishes to the gateway.
 *
 * The frontend's useChatSocket hook publishes:
 *   /app/chat/send         — { conversationId, senderId, content }
 *   /app/friends/snapshot  — {} (request a re-emit of the presence snapshot)
 *
 * sendMessage just forwards to chat-service over gRPC; the chat-service
 * fan-out then comes back through {@link WsBridgeService} as a MESSAGE
 * event on /user/queue/messages/{convId}, so the sender's UI will see
 * the same frame everyone else does (no optimistic appending needed).
 *
 * The presence snapshot endpoint exists to close a race: chat-service
 * pushes the initial PRESENCE_SNAPSHOT immediately on subscribeEvents,
 * which can land before the frontend subscribes to /user/queue/friends.
 * The frontend backstop is to publish /app/friends/snapshot once it's
 * subscribed; we replay the cached snapshot from {@link WsBridgeService}.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatClient chat;
    private final WsBridgeService bridge;

    @MessageMapping("/chat/send")
    public void send(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous /chat/send dropped");
            return;
        }
        long me = Long.parseLong(principal.getName());

        Object convIdObj = payload.get("conversationId");
        Object senderIdObj = payload.get("senderId");
        Object contentObj = payload.get("content");
        if (convIdObj == null || contentObj == null) {
            log.warn("Malformed /chat/send from user {}: {}", me, payload);
            return;
        }

        long conversationId = ((Number) convIdObj).longValue();
        String content = String.valueOf(contentObj);
        // The frontend includes its own senderId, but never trust the client —
        // the principal is the only authenticated identity we have.
        long senderId = (senderIdObj instanceof Number n) ? n.longValue() : me;
        if (senderId != me) {
            log.warn("Spoofed senderId in /chat/send: principal={} payload sender={}", me, senderId);
            senderId = me;
        }

        try {
            chat.send(conversationId, senderId, content);
        } catch (Exception e) {
            log.warn("chat.send failed for user {} conv {}: {}", me, conversationId, e.getMessage());
        }
    }

    @MessageMapping("/friends/snapshot")
    public void snapshot(Principal principal) {
        if (principal == null) return;
        long me = Long.parseLong(principal.getName());
        bridge.replaySnapshot(me);
    }
}
