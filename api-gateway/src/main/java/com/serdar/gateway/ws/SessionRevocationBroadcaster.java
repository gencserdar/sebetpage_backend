package com.serdar.gateway.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Push immediate logout to a revoked browser tab over the existing friends queue. */
@Component
@RequiredArgsConstructor
public class SessionRevocationBroadcaster {

    private static final String DESTINATION = "/queue/friends";

    private final SimpMessagingTemplate stomp;

    public void sessionRevoked(long userId, long sessionId) {
        if (userId <= 0 || sessionId <= 0) return;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "SESSION_REVOKED");
        event.put("sessionId", sessionId);
        stomp.convertAndSendToUser(String.valueOf(userId), DESTINATION, event);
    }
}
