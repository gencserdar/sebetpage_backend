package com.serdar.personal.config;

import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.CustomUserDetailsService;
import com.serdar.personal.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

import java.util.Map;
import java.util.Optional;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired private JwtService jwtService;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest req,
                                   ServerHttpResponse res,
                                   WebSocketHandler handler,
                                   Map<String, Object> attrs) {

        HttpServletRequest http = ((ServletServerHttpRequest) req).getServletRequest();
        String access = getCookie(http, "jwt-token");
        if (access == null) return reject(res, "no access token");

        /* ---------- try to read the subject WITHOUT validating ---------- */
        String username;
        try {                                // extractUsername never checks expiry
            username = jwtService.extractUsername(access);
        } catch (Exception e) {              // malformed signature
            return reject(res, "token unreadable");
        }

        /* ---------- is the token still valid? Wrap in its OWN try/catch ---------- */
        try {
            UserDetails ud = userDetailsService.loadUserByUsername(username);
            if (jwtService.isTokenValid(access, ud)) {           // <= may throw
                attrs.put("username", username);
                return true;                                     // handshake OK
            }
            // signature invalid but not expired
            return reject(res, "access token invalid");

        } catch (io.jsonwebtoken.ExpiredJwtException ignored) {
            /* ---------- access token ONLY expired – fall back to refresh ---------- */
        }

        String refresh = getCookie(http, "refreshToken");
        if (refresh == null) return reject(res, "no refresh token");

        UserDetails ud = userDetailsService.loadUserByUsername(username);
        if (!jwtService.isTokenValid(refresh, ud)) {
            return reject(res, "refresh invalid");
        }

        /* ---------- issue a brand-new short-lived access token ---------- */
        User entity = userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        String newAccess = jwtService.generateToken(entity);
        res.getHeaders().add("Set-Cookie",
                "jwt-token=" + newAccess + "; Path=/; SameSite=Lax");

        attrs.put("username", username);
        return true;                                             // handshake OK
    }


    /* ---------- helpers ---------- */

    private boolean reject(ServerHttpResponse res, String reason) {
        System.out.println("Handshake failed: " + reason);
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    private String getCookie(HttpServletRequest req, String name) {
        Cookie c = WebUtils.getCookie(req, name);
        return c != null ? c.getValue() : null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception ex) {
        if (ex != null) {
            System.out.println("WebSocket handshake exception: " + ex.getMessage());
        }
    }
}
