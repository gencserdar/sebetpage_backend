package com.serdar.gateway.controller;

import com.serdar.common.config.ProductionTransportValidator;
import com.serdar.gateway.client.AuthClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.dto.Dtos;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.gateway.security.SessionRevocationCache;
import com.serdar.gateway.ws.SessionRevocationBroadcaster;
import com.serdar.proto.auth.AuthResponse;
import com.serdar.proto.auth.SessionInfo;
import com.serdar.proto.auth.SessionList;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthClient auth;
    private final UserClient users;
    private final SessionRevocationCache revokedSessions;
    private final SessionRevocationBroadcaster sessionEvents;

    @Value("${app.environment}") private String env;
    @Value("${frontend.base-url}") private String frontendBaseUrl;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Dtos.RegisterRequest req) {
        // Step 1: create credentials in auth-service (source of truth for uniqueness).
        var reg = auth.register(req.getEmail(), req.getPassword(), req.getNickname(), req.getName(), req.getSurname());
        // Step 2: mirror profile in user-service.
        // Compensation: if step 2 fails, roll back the unactivated credential.
        try {
            users.createProfile(reg.getUserId(), reg.getEmail(), reg.getNickname(), req.getName(), req.getSurname());
        } catch (RuntimeException e) {
            boolean rolledBack = false;
            for (int attempt = 0; attempt < 2 && !rolledBack; attempt++) {
                try {
                    auth.abortRegistration(reg.getUserId());
                    rolledBack = true;
                } catch (RuntimeException rollbackEx) {
                    log.error("Registration rollback attempt {} failed for user {}: {}",
                            attempt + 1, reg.getUserId(), rollbackEx.getMessage());
                }
            }
            if (!rolledBack) {
                log.error("Orphan credential may remain for user {} ({})",
                        reg.getUserId(), req.getEmail());
            }
            throw e;
        }
        return ResponseEntity.ok("Registration successful. Please check your email to activate your account.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Dtos.AuthRequest req, HttpServletResponse resp) {
        AuthResponse r = auth.login(req.getEmail(), req.getPassword(), req.isRememberMe());
        writeRefreshCookie(resp, r.getRefreshToken(), r.getRefreshCookieAgeSeconds());
        clearLegacyAccessCookie(resp);
        return ResponseEntity.ok(new Dtos.AuthResponse(r.getAccessToken(), r.getFrozen()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse resp) {
        Optional<String> rt = readRefreshCookie(req);
        if (rt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error", "No refresh token"));
        AuthResponse r = auth.refresh(rt.get());
        // auth-service rotated the refresh token — write the new one back so
        // the next /refresh call uses a token that actually matches the DB row.
        writeRefreshCookie(resp, r.getRefreshToken(), r.getRefreshCookieAgeSeconds());
        clearLegacyAccessCookie(resp);
        return ResponseEntity.ok(new Dtos.AuthResponse(r.getAccessToken(), r.getFrozen()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse resp) {
        // Always clear the cookie — the user is signed out client-side regardless.
        //
        // Session invalidation has two paths:
        //   Primary  : session id from the access token "sid" claim (normal flow).
        //   Fallback : raw refresh token from the HttpOnly cookie — used when the
        //              1-minute access token has already expired at logout time.
        //              Auth-service hashes it and finds the session by hash.
        //
        // Passing both means auth-service can always identify the session, even
        // if the access token just expired between the last refresh and logout.
        long sessionId = 0;
        try { sessionId = CurrentUser.require().sessionId(); } catch (Exception ignored) {}

        String refreshToken = readRefreshCookie(req).orElse("");

        if (sessionId > 0 || !refreshToken.isBlank()) {
            try {
                auth.logout(sessionId, refreshToken);
            } catch (Exception ignored) {}
        }

        clearRefreshCookie(resp);
        clearLegacyAccessCookie(resp);
        return ResponseEntity.ok("Logged out");
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        var user = CurrentUser.require();
        SessionList sessions = auth.listSessions(user.id(), user.sessionId());
        var rows = sessions.getSessionsList().stream()
                .map(s -> Map.of(
                        "id", s.getId(),
                        "createdAtMillis", s.getCreatedAtMillis(),
                        "expiresAtMillis", s.getExpiresAtMillis(),
                        "rememberMe", s.getRememberMe(),
                        "current", s.getCurrent()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<?> revokeSession(@PathVariable long sessionId) {
        var user = CurrentUser.require();
        revokeSessionForUser(user.id(), sessionId);
        return ResponseEntity.ok("Session revoked");
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletResponse resp) {
        var user = CurrentUser.require();
        boolean excludeCurrent = body != null && Boolean.TRUE.equals(body.get("excludeCurrent"));
        SessionList sessions = auth.listSessions(user.id(), user.sessionId());
        if (excludeCurrent) {
            sessions.getSessionsList().stream()
                    .filter(s -> !s.getCurrent())
                    .forEach(s -> revokeSessionForUser(user.id(), s.getId()));
            return ResponseEntity.ok("Signed out from other devices");
        }
        auth.logoutAll(user.id());
        for (SessionInfo s : sessions.getSessionsList()) {
            revokedSessions.markRevoked(s.getId());
            sessionEvents.sessionRevoked(user.id(), s.getId());
        }
        clearRefreshCookie(resp);
        clearLegacyAccessCookie(resp);
        return ResponseEntity.ok("Logged out from all devices");
    }

    private void revokeSessionForUser(long userId, long sessionId) {
        auth.revokeSession(userId, sessionId);
        revokedSessions.markRevoked(sessionId);
        sessionEvents.sessionRevoked(userId, sessionId);
    }

    /**
     * Legacy activation emails linked here directly. Redirect to the SPA so old
     * inboxes keep working after activation moved to the frontend.
     */
    @GetMapping("/activate")
    public ResponseEntity<Void> activateRedirect(@RequestParam String code) {
        String location = frontendBaseUrl + "/activate?code="
                + URLEncoder.encode(code, StandardCharsets.UTF_8);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activate(@RequestParam String code) {
        auth.activate(code);
        return ResponseEntity.ok("Account activated successfully!");
    }

    @PostMapping("/resend-activation")
    public ResponseEntity<?> resendActivation(@RequestParam String email) {
        auth.resendActivation(email);
        return ResponseEntity.ok(
                "If an unactivated account exists for this email, a new activation link was sent.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@RequestParam String email) {
        auth.forgotPassword(email);
        return ResponseEntity.ok("Reset link sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> reset(@RequestParam String code, @RequestBody Dtos.ResetPasswordRequest req) {
        boolean ok = auth.resetPassword(code, req.getNewPassword());
        return ok ? ResponseEntity.ok("Password reset successful.")
                  : ResponseEntity.badRequest().body(Map.of("error", "Invalid reset code"));
    }

    // --- cookie helpers -----------------------------------------------------

    private void clearRefreshCookie(HttpServletResponse resp) {
        ResponseCookie c = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(ProductionTransportValidator.isProductionLike(env)).sameSite("Lax").path("/").maxAge(0).build();
        resp.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private void writeRefreshCookie(HttpServletResponse resp, String token, int maxAge) {
        // Use ResponseCookie so we can explicitly set SameSite — the classic
        // jakarta.servlet Cookie doesn't expose it pre-Servlet 6.1 and browsers
        // differ on their default when the attribute is missing.
        ResponseCookie c = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(ProductionTransportValidator.isProductionLike(env))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private void clearLegacyAccessCookie(HttpServletResponse resp) {
        ResponseCookie c = ResponseCookie.from("jwt-token", "")
                .httpOnly(true)
                .secure(ProductionTransportValidator.isProductionLike(env))
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private Optional<String> readRefreshCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return Optional.empty();
        return Arrays.stream(req.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue).findFirst();
    }
}
