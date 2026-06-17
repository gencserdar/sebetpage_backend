package com.serdar.gateway.ws;

import com.serdar.common.grpc.GrpcActorContext;
import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.security.RedisTokenBucketRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP handlers for client publishes to the gateway.
 *
 * Message sends are forwarded to chat-service over gRPC. The chat-service
 * fan-out then returns through WsBridgeService, so every participant receives
 * the same server-accepted message frame.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatClient chat;
    private final WsBridgeService bridge;
    private final RedisTokenBucketRateLimiter limiter;

    @Value("${app.rate-limit.chat-send.capacity}")
    private long chatSendCapacity;

    @Value("${app.rate-limit.chat-send.window-seconds}")
    private long chatSendWindowSeconds;

    @MessageMapping("/chat/send")
    public void send(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous /chat/send dropped");
            return;
        }
        long me = Long.parseLong(principal.getName());
        if (!limiter.tryAcquire("rl:chat-send:user:" + me, chatSendCapacity, chatSendWindowSeconds)) {
            log.warn("Rate-limited /chat/send for user {}", me);
            return;
        }

        Object convIdObj = payload.get("conversationId");
        Object senderIdObj = payload.get("senderId");
        Object contentObj = payload.get("content");
        if (convIdObj == null || contentObj == null) {
            log.warn("Malformed /chat/send from user {}: {}", me, payload);
            return;
        }

        Long parsedConversationId = asLong(convIdObj);
        if (parsedConversationId == null) {
            log.warn("Malformed /chat/send conversationId from user {}: {}", me, payload);
            return;
        }

        long conversationId = parsedConversationId;
        String content = String.valueOf(contentObj);
        Long parsedSenderId = asLong(senderIdObj);
        long senderId = parsedSenderId == null ? me : parsedSenderId;
        if (senderId != me) {
            log.warn("Spoofed senderId in /chat/send: principal={} payload sender={}", me, senderId);
            senderId = me;
        }

        final long actorId = senderId;
        try {
            GrpcActorContext.runAs(actorId, () -> chat.send(conversationId, actorId, content));
        } catch (Exception e) {
            log.warn("chat.send failed for user {} conv {}: {}", me, conversationId, e.getMessage());
        }
    }

    @MessageMapping("/chat/typing")
    public void typing(@Payload Map<String, Object> payload, Principal principal) {
        if (principal == null) return;
        long me = Long.parseLong(principal.getName());
        Long conversationId = asLong(payload.get("conversationId"));
        if (conversationId == null) return;
        try {
            GrpcActorContext.runAs(me, () -> chat.notifyTyping(conversationId, me));
        } catch (Exception e) {
            log.warn("chat.notifyTyping failed for user {} conv {}: {}", me, conversationId, e.getMessage());
        }
    }

    @MessageMapping("/friends/snapshot")
    public void snapshot(Principal principal) {
        if (principal == null) return;
        long me = Long.parseLong(principal.getName());
        bridge.replaySnapshot(me);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
