package com.serdar.gateway.controller;

import com.serdar.gateway.client.AuthClient;
import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.client.CommunityClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.dto.Dtos;
import com.serdar.gateway.mapper.ProfileSettingsMapper;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.gateway.ws.EntityEventBroadcaster;
import com.serdar.proto.auth.Credentials;
import com.serdar.proto.user.BlockStatusResponse;
import com.serdar.proto.user.ProfileSettings;
import com.serdar.proto.user.UserProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.serdar.common.config.ProductionTransportValidator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User profile endpoints. The gateway joins auth-service credentials and
 * user-service profile rows into the UserDTO the frontend expects.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthClient auth;
    private final UserClient users;
    private final ChatClient chats;
    private final CommunityClient communities;
    private final EntityEventBroadcaster broadcaster;
    private final ProfileSettingsMapper profileSettingsMapper;

    @Value("${app.environment}") private String env;

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        long id = CurrentUser.require().id();
        return ResponseEntity.ok(join(auth.byId(id), users.byId(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> byId(@PathVariable long id) {
        long viewerId = CurrentUser.require().id();
        if (isBlockedEitherWay(viewerId, id)) {
            return ResponseEntity.notFound().build();
        }
        UserProfile p = users.byId(id);
        Credentials c = auth.byId(id);
        return ResponseEntity.ok(profileForViewer(viewerId, c, p));
    }

    @GetMapping("/profile/{nickname}")
    public ResponseEntity<?> byNickname(@PathVariable String nickname) {
        UserProfile p = users.byNickname(nickname);
        Credentials c = auth.byId(p.getId());
        long viewerId = CurrentUser.require().id();
        if (isBlockedEitherWay(viewerId, p.getId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profileForViewer(viewerId, c, p));
    }

    @GetMapping("/{id}/profile-settings")
    public ResponseEntity<?> profileSettings(@PathVariable long id) {
        long viewerId = CurrentUser.require().id();
        if (isBlockedEitherWay(viewerId, id)) {
            return ResponseEntity.notFound().build();
        }
        Credentials c = auth.byId(id);
        if (viewerId != id && c.getFrozen()) {
            return ResponseEntity.ok(emptyProfileSettings(id));
        }
        ProfileSettings settings = users.getProfileSettings(id);
        return ResponseEntity.ok(profileSettingsMapper.fromProto(settings));
    }

    @PutMapping("/profile-settings")
    public ResponseEntity<?> updateProfileSettings(@RequestBody Dtos.UpdateProfileSettingsRequest body) {
        long id = CurrentUser.require().id();
        try {
            ProfileSettings updated = users.updateProfileSettings(
                    id,
                    body.getBio() == null ? "" : body.getBio(),
                    profileSettingsMapper.toSocialLinksJson(body.getSocialLinks()),
                    profileSettingsMapper.toProfileCardJson(body.getProfileCard()));
            return ResponseEntity.ok(profileSettingsMapper.fromProto(updated));
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid profile settings payload"));
        }
    }

    // --- nickname (single-step, no email confirmation) ---------------------

    @PostMapping("/change-nickname")
    public ResponseEntity<?> changeNickname(@RequestParam String newNickname) {
        long id = CurrentUser.require().id();
        UserProfile updated = users.changeNickname(id, newNickname);
        // Real-time fan-out so any client holding a stale "user.nickname" gets
        // updated immediately — fixes the click-stale-nickname-and-404 bug.
        broadcaster.userUpdated(updated);
        return ResponseEntity.ok(new Dtos.FieldUpdateResponse("nickname", updated.getNickname()));
    }

    // --- email change: two-step with a 6-digit code to the new address -----
    //
    // Step 1: POST /api/user/request-email-change?newEmail=...
    //   auth-service stashes a code on the credential row and emails the
    //   new address. Profile email isn't touched yet.
    // Step 2: POST /api/user/confirm-email-change?code=...
    //   auth-service validates the code, swaps the email, and notifies the
    //   old address. The gateway then mirrors the new email onto the
    //   user-service profile row so denormalized reads stay consistent.

    @PostMapping("/request-email-change")
    public ResponseEntity<?> requestEmailChange(@RequestParam String newEmail) {
        long id = CurrentUser.require().id();
        auth.requestEmailChange(id, newEmail);
        return ResponseEntity.ok(Map.of("status", "code_sent"));
    }

    @PostMapping("/confirm-email-change")
    public ResponseEntity<?> confirmEmailChange(@RequestParam String code) {
        long id = CurrentUser.require().id();
        String newEmail = auth.confirmEmailChange(id, code);
        // Mirror onto user-service profile and re-fetch the row so the
        // broadcast carries the up-to-date snapshot.
        UserProfile updated = users.syncProfileEmail(id, newEmail);
        broadcaster.userUpdated(updated);
        return ResponseEntity.ok(new Dtos.FieldUpdateResponse("email", newEmail));
    }

    // --- password change: two-step with a 6-digit code to current address --

    @PostMapping("/request-password-change")
    public ResponseEntity<?> requestPasswordChange(@RequestBody Dtos.ChangePasswordRequest req) {
        long id = CurrentUser.require().id();
        auth.requestPasswordChange(id, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("status", "code_sent"));
    }

    @PostMapping("/confirm-password-change")
    public ResponseEntity<?> confirmPasswordChange(@RequestParam String code) {
        long id = CurrentUser.require().id();
        auth.confirmPasswordChange(id, code);
        return ResponseEntity.ok("Password changed");
    }

    // Legacy direct endpoints retained for callers that haven't migrated yet.
    // They go through the verified flow under the hood — request a code and
    // immediately fail with a hint, forcing migration. Frontends still on the
    // old paths get a clear error instead of a silent stale state.
    @PostMapping("/change-password")
    public ResponseEntity<?> changePasswordLegacy() {
        return ResponseEntity.status(410)
                .body(Map.of("error", "Use /request-password-change then /confirm-password-change"));
    }

    @PostMapping("/change-email")
    public ResponseEntity<?> changeEmailLegacy() {
        return ResponseEntity.status(410)
                .body(Map.of("error", "Use /request-email-change then /confirm-email-change"));
    }

    @PostMapping("/update-name-surname")
    public ResponseEntity<?> updateNameSurname(@RequestParam(required = false) String name,
                                               @RequestParam(required = false) String surname) {
        long id = CurrentUser.require().id();
        UserProfile p = users.updateNameSurname(id, name, surname);
        broadcaster.userUpdated(p);
        return ResponseEntity.ok(Map.of("name", p.getName(), "surname", p.getSurname()));
    }

    @PostMapping(value = "/profile-photo", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        long id = CurrentUser.require().id();
        UserProfile p = users.updatePhoto(id, file.getBytes(), file.getContentType(), file.getOriginalFilename());
        broadcaster.userUpdated(p);
        return ResponseEntity.ok(Map.of("profileImageUrl", p.getProfileImageUrl()));
    }

    @PostMapping("/freeze")
    public ResponseEntity<?> freeze() {
        long id = CurrentUser.require().id();
        auth.freezeAccount(id);
        broadcaster.userUpdated(users.byId(id));
        return ResponseEntity.ok(Map.of("status", "frozen"));
    }

    @PostMapping("/unfreeze")
    public ResponseEntity<?> unfreeze() {
        long id = CurrentUser.require().id();
        auth.unfreezeAccount(id);
        broadcaster.userUpdated(users.byId(id));
        return ResponseEntity.ok(Map.of("status", "active"));
    }

    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(HttpServletResponse resp) {
        long id = CurrentUser.require().id();
        chats.deleteUserData(id);
        communities.deleteUserData(id);
        users.deleteUserData(id);
        auth.deleteAccount(id);
        clearAuthCookies(resp);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // --- helpers -----------------------------------------------------------

    private void clearAuthCookies(HttpServletResponse resp) {
        boolean secure = ProductionTransportValidator.isProductionLike(env);
        ResponseCookie refresh = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie legacyAccess = ResponseCookie.from("jwt-token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        resp.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
        resp.addHeader(HttpHeaders.SET_COOKIE, legacyAccess.toString());
    }

    private Dtos.UserDTO profileForViewer(long viewerId, Credentials c, UserProfile p) {
        if (viewerId == p.getId()) {
            return join(c, p);
        }
        if (c.getFrozen()) {
            return frozenPublicProfile(c, p);
        }
        return publicProfile(c, p);
    }

    private boolean isBlockedEitherWay(long viewerId, long targetId) {
        if (viewerId == targetId) return false;
        BlockStatusResponse status = users.blockStatus(viewerId, targetId);
        return status.getEither();
    }

    private static Dtos.ProfileSettingsDTO emptyProfileSettings(long userId) {
        return Dtos.ProfileSettingsDTO.builder()
                .userId(userId)
                .bio("")
                .socialLinks(Collections.emptyList())
                .profileCard(new Dtos.ProfileCardDTO(Collections.emptyList()))
                .build();
    }

    /** Frozen account as seen by others — nickname only. */
    static Dtos.UserDTO frozenPublicProfile(Credentials c, UserProfile p) {
        return Dtos.UserDTO.builder()
                .id(p.getId())
                .nickname(p.getNickname())
                .frozen(true)
                .activated(c.getActivated())
                .role(c.getRole().name())
                .build();
    }

    private static Dtos.UserDTO join(Credentials c, UserProfile p) {
        return Dtos.UserDTO.builder()
                .id(p.getId())
                .email(p.getEmail())
                .nickname(p.getNickname())
                .name(p.getName())
                .surname(p.getSurname())
                .profileImageUrl(p.getProfileImageUrl())
                .activated(c.getActivated())
                .frozen(c.getFrozen())
                .role(c.getRole().name())
                .build();
    }

    /** Public profile view — email is omitted for other users. */
    static Dtos.UserDTO publicProfile(Credentials c, UserProfile p) {
        return Dtos.UserDTO.builder()
                .id(p.getId())
                .nickname(p.getNickname())
                .name(p.getName())
                .surname(p.getSurname())
                .profileImageUrl(p.getProfileImageUrl())
                .activated(c.getActivated())
                .role(c.getRole().name())
                .build();
    }
}
