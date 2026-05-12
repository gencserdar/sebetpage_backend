package com.serdar.gateway.controller;

import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.chat.Conversation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        Conversation c = chat.addMessagingGroupMember(groupId, me, req.userId());
        return ResponseEntity.ok(toRow(c));
    }

    // --- request records ---------------------------------------------------

    record CreateGroupRequest(List<Long> memberIds, String name) {}
    record AddMemberRequest(long userId) {}

    // --- helpers -----------------------------------------------------------

    private static Map<String, Object> toRow(Conversation c) {
        return Map.of(
                "id",              c.getId(),
                "type",            c.getType().name(),
                "title",           c.getTitle(),
                "createdAtMillis", c.getCreatedAtMillis()
        );
    }
}
