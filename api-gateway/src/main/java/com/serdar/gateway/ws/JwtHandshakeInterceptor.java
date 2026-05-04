package com.serdar.gateway.ws;

import com.serdar.gateway.client.AuthClient;
import com.serdar.proto.auth.ValidateTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket handshake gate: browser clients use a short-lived `?ticket=`
 * value minted by /api/ws-ticket, while non-browser clients may use
 * `Authorization: Bearer`. On success, the user's id is stashed in the WS
 * attributes so the STOMP bridge knows who to subscribe.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthClient auth;
    private final WsTicketService tickets;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String ticket = resolveQueryParam(request, "ticket");
        if (ticket != null) {
            return tickets.consume(ticket, clientAddress(request), userAgent(request)).map(t -> {
                attributes.put("userId", t.userId());
                attributes.put("nickname", t.nickname());
                attributes.put("email", t.email());
                return true;
            }).orElse(false);
        }

        String token = resolveBearerToken(request);
        if (token == null) return false;
        try {
            ValidateTokenResponse v = auth.validate(token);
            if (!v.getValid()) return false;
            attributes.put("userId", v.getUserId());
            attributes.put("nickname", v.getNickname());
            attributes.put("email", v.getEmail());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}

    private String resolveQueryParam(ServerHttpRequest request, String name) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            String q = servletReq.getServletRequest().getParameter(name);
            if (q != null && !q.isBlank()) return q;
        }
        return null;
    }

    private String resolveBearerToken(ServerHttpRequest request) {
        var auths = request.getHeaders().get("Authorization");
        if (auths != null && !auths.isEmpty() && auths.get(0).startsWith("Bearer ")) {
            return auths.get(0).substring(7);
        }
        return null;
    }

    private String clientAddress(ServerHttpRequest request) {
        return request.getRemoteAddress() == null ? "" : request.getRemoteAddress().getAddress().getHostAddress();
    }

    private String userAgent(ServerHttpRequest request) {
        var agents = request.getHeaders().get("User-Agent");
        return agents == null || agents.isEmpty() ? "" : agents.get(0);
    }
}
