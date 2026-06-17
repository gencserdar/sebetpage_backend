package com.serdar.gateway.ws;

import com.serdar.gateway.security.SessionRevocationCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Drops STOMP client publishes when the bound session was revoked remotely.
 * Complements {@link com.serdar.gateway.security.JwtAuthFilter} which re-validates
 * the session row on every HTTP request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStompChannelInterceptor implements ChannelInterceptor {

    private final SessionRevocationCache revokedSessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            return message;
        }
        Object rawSessionId = accessor.getSessionAttributes() == null
                ? null
                : accessor.getSessionAttributes().get("sessionId");
        long sessionId = rawSessionId instanceof Number n ? n.longValue() : 0L;
        if (sessionId > 0 && revokedSessions.isRevoked(sessionId)) {
            log.debug("Blocked STOMP {} — session {} revoked", destination, sessionId);
            return null;
        }
        return message;
    }
}
