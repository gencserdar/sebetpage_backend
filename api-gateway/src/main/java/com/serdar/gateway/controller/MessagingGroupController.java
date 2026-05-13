package com.serdar.gateway.controller;

import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.chat.Conversation;
import com.serdar.proto.chat.MessagingGroupDetail;
import com.serdar.proto.chat.MessagingGroupParticipant;
import com.serdar.proto.chat.MessagingGroupPermissionSet;
import com.serdar.proto.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST surface for messaging-group operations.
 *
 * "Messaging groups" are private group chats created from the + button in a
 * direct chat. They are stored as Conversation rows with type=MESSAGING_GROUP
 * and are intentionally kept separate from future post-based GROUP conversations.
 *
 * All endpoints require a valid access token — secured by JwtAuthFilter.
 */
@RestController
@RequestMapping("/api/messaging-groups")
@RequiredArgsConstructor
public class MessagingGroupController {

    private final ChatClient chat;
    private final UserClient users;

    /**
     * Create a new messaging group.
     *
     * Body: { "memberIds": [long, ...], "name": "optional name" }
     *
     * The caller is automatically added as ADMIN; memberIds are added as MEMBER.
     * At least one memberId (besides the caller) is required.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateGroupRequest req) {
        long me = CurrentUser.require().id();
        if (req.memberIds() == null || req.memberIds().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "memberIds must not be empty"));

        Conversation c = chat.createMessagingGroup(me, req.memberIds(), req.name());
        return ResponseEntity.ok(toRow(c));
    }

    /**
     * Add a new member to an existing messaging group.
     *
     * Body: { "userId": long }
     *
     * Caller must already be a member. Adding an existing member is a no-op.
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<?> addMember(@PathVariable long groupId,
                                       @RequestBody AddMemberRequest req) {
        long me = CurrentUser.require().id();
        chat.addMessagingGroupMember(groupId, me, req.userId());
        return ResponseEntity.ok(toDetail(chat.messagingGroupDetail(groupId, me)));
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable long groupId,
                                          @PathVariable long userId) {
        long me = CurrentUser.require().id();
        return ResponseEntity.ok(toDetail(chat.removeMessagingGroupMember(groupId, me, userId)));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<?> detail(@PathVariable long groupId) {
        long me = CurrentUser.require().id();
        return ResponseEntity.ok(toDetail(chat.messagingGroupDetail(groupId, me)));
    }

    @PatchMapping("/{groupId}")
    public ResponseEntity<?> updateGroup(@PathVariable long groupId,
                                         @RequestBody Map<String, Object> body) {
        long me = CurrentUser.require().id();
        boolean updateTitle = body.containsKey("title");
        boolean updateDescription = body.containsKey("description");
        if (body.containsKey("imageUrl")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Use the photo upload endpoint"));
        }
        MessagingGroupDetail detail = chat.updateMessagingGroup(
                groupId,
                me,
                updateTitle,
                asString(body.get("title")),
                updateDescription,
                asString(body.get("description")),
                false,
                null
        );
        return ResponseEntity.ok(toDetail(detail));
    }

    @PostMapping(value = "/{groupId}/photo", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadPhoto(@PathVariable long groupId,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        long me = CurrentUser.require().id();
        MessagingGroupDetail before = chat.messagingGroupDetail(groupId, me);
        if (!canChangePhoto(before)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Missing permission"));
        }
        String imageUrl = users.uploadImage(me, file.getBytes(), file.getContentType(), file.getOriginalFilename());
        MessagingGroupDetail detail = chat.updateMessagingGroup(
                groupId,
                me,
                false,
                null,
                false,
                null,
                true,
                imageUrl
        );
        return ResponseEntity.ok(toDetail(detail));
    }

    @PatchMapping("/{groupId}/participants/{userId}")
    public ResponseEntity<?> updateParticipant(@PathVariable long groupId,
                                               @PathVariable long userId,
                                               @RequestBody Map<String, Object> body) {
        long me = CurrentUser.require().id();
        boolean updateMuted = body.containsKey("muted");
        boolean updatePermissions = body.containsKey("permissions");
        MessagingGroupPermissionSet permissions = permissionSet(body.get("permissions"));
        MessagingGroupDetail detail = chat.updateMessagingGroupParticipant(
                groupId,
                me,
                userId,
                updateMuted,
                asBoolean(body.get("muted")),
                updatePermissions,
                permissions
        );
        return ResponseEntity.ok(toDetail(detail));
    }

    @DeleteMapping("/{groupId}/members/me")
    public ResponseEntity<?> exitGroup(@PathVariable long groupId) {
        long me = CurrentUser.require().id();
        chat.exitMessagingGroup(groupId, me);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable long groupId) {
        long me = CurrentUser.require().id();
        chat.deleteMessagingGroup(groupId, me);
        return ResponseEntity.noContent().build();
    }

    // --- request records ---------------------------------------------------

    record CreateGroupRequest(List<Long> memberIds, String name) {}
    record AddMemberRequest(long userId) {}

    // --- helpers -----------------------------------------------------------

    private static Map<String, Object> toRow(Conversation c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.getId());
        row.put("type", c.getType().name());
        row.put("title", c.getTitle());
        row.put("description", c.getDescription());
        row.put("imageUrl", c.getImageUrl());
        row.put("createdById", c.getCreatedById());
        row.put("createdAtMillis", c.getCreatedAtMillis());
        return row;
    }

    private Map<String, Object> toDetail(MessagingGroupDetail detail) {
        Map<String, Object> row = new LinkedHashMap<>(toRow(detail.getConversation()));
        row.put("me", toParticipant(detail.getMe()));
        row.put("participants", detail.getParticipantsList().stream().map(this::toParticipant).toList());
        row.put("knownParticipants", detail.getKnownParticipantsList().stream().map(this::toParticipant).toList());
        return row;
    }

    private Map<String, Object> toParticipant(MessagingGroupParticipant participant) {
        Map<String, Object> row = new LinkedHashMap<>();
        UserProfile profile = safeProfile(participant.getUserId());
        row.put("userId", participant.getUserId());
        row.put("id", participant.getUserId());
        row.put("nickname", profile == null ? "User " + participant.getUserId() : profile.getNickname());
        row.put("name", profile == null ? "" : profile.getName());
        row.put("surname", profile == null ? "" : profile.getSurname());
        row.put("profileImageUrl", profile == null ? "" : profile.getProfileImageUrl());
        row.put("role", participant.getRole());
        row.put("muted", participant.getMuted());
        row.put("permissions", toPermissions(participant.getPermissions()));
        return row;
    }

    private UserProfile safeProfile(long userId) {
        try {
            return users.byId(userId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> toPermissions(MessagingGroupPermissionSet p) {
        return Map.of(
                "canChangePhoto", p.getCanChangePhoto(),
                "canChangeDescription", p.getCanChangeDescription(),
                "canChangeName", p.getCanChangeName(),
                "canRemoveMembers", p.getCanRemoveMembers(),
                "canAddMembers", p.getCanAddMembers()
        );
    }

    private static MessagingGroupPermissionSet permissionSet(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return MessagingGroupPermissionSet.getDefaultInstance();
        }
        return MessagingGroupPermissionSet.newBuilder()
                .setCanChangePhoto(asBoolean(map.get("canChangePhoto")))
                .setCanChangeDescription(asBoolean(map.get("canChangeDescription")))
                .setCanChangeName(asBoolean(map.get("canChangeName")))
                .setCanRemoveMembers(asBoolean(map.get("canRemoveMembers")))
                .setCanAddMembers(asBoolean(map.get("canAddMembers")))
                .build();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private static boolean canChangePhoto(MessagingGroupDetail detail) {
        MessagingGroupParticipant me = detail.getMe();
        return detail.getConversation().getCreatedById() == me.getUserId()
                || "ADMIN".equalsIgnoreCase(me.getRole())
                || me.getPermissions().getCanChangePhoto();
    }
}
