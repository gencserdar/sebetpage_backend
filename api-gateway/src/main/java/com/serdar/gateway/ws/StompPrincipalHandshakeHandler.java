package com.serdar.gateway.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Pins a {@link Principal} onto the STOMP session by reading the user id the
 * handshake interceptor stashed. The principal's name is the stringified user
 * id; controllers fish it back out via {@code @Header("simpUser")}.
 */
@Component
public class StompPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Object uid = attributes.get("userId");
        if (uid == null) return null;
        String name = String.valueOf(uid);
        return () -> name;
    }
}
