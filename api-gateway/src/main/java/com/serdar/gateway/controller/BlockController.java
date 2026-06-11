package com.serdar.gateway.controller;

import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.user.Block;
import com.serdar.proto.user.BlockStatusResponse;
import com.serdar.proto.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Block endpoints. Paths match what the frontend's blockService expects
 * (aligned with the original monolith routes):
 *   POST   /api/blocks/{userId}            — block a user by numeric id
 *   DELETE /api/blocks/{userId}            — unblock
 *   GET    /api/blocks                     — list users I've blocked
 *   GET    /api/blocks/status/{nickname}   — block status by nickname
 */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final UserClient users;
    private final SimpMessagingTemplate stomp;

    @PostMapping("/{userId}")
    public ResponseEntity<?> block(@PathVariable long userId) {
        long me = CurrentUser.require().id();

        // Resolve profiles up-front so we can describe each side in the WS
        // event without another RPC after the block lands.
        UserProfile myProfile;
        UserProfile otherProfile;
        try {
            myProfile = users.byId(me);
            otherProfile = users.byId(userId);
        } catch (Exception e) {
            myProfile = null;
            otherProfile = null;
        }

        users.block(me, userId);

        notifyBlockStatusChanged(me, userId);

        // Reuse FRIEND_REMOVED — FriendChat already listens for it and locks
        // the input on a match by email/nickname. From the chat's perspective
        // a block is the same outcome as a removal: the other side is gone.
        stomp.convertAndSendToUser(String.valueOf(me), "/queue/friends", Map.of(
                "type", "FRIEND_REMOVED",
                "userId", userId,
                "removedFriend", otherProfile == null ? Map.of() : summary(otherProfile)
        ));
        stomp.convertAndSendToUser(String.valueOf(userId), "/queue/friends", Map.of(
                "type", "FRIEND_REMOVED",
                "userId", me,
                "removedFriend", myProfile == null ? Map.of() : summary(myProfile)
        ));

        return ResponseEntity.ok(Map.of("status", "blocked", "blockedId", userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> unblock(@PathVariable long userId) {
        long me = CurrentUser.require().id();
        users.unblock(me, userId);
        notifyBlockStatusChanged(me, userId);
        return ResponseEntity.ok(Map.of("status", "unblocked", "blockedId", userId));
    }

    @GetMapping
    public ResponseEntity<?> myBlocks() {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> rows = users.myBlocks(me).getBlocksList().stream()
                .map(BlockController::toRow).toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/status/{nickname}")
    public ResponseEntity<?> status(@PathVariable String nickname) {
        long me = CurrentUser.require().id();
        // The gRPC blockStatus is id-based, so we resolve the nickname to an id
        // here. Frontend ProfilePopup only ever has the nickname at this point.
        UserProfile other = users.byNickname(nickname);
        BlockStatusResponse r = users.blockStatus(me, other.getId());
        return ResponseEntity.ok(Map.of(
                "blockedByMe", r.getBlockedByMe(),
                "blocksMe", r.getBlocksMe(),
                "either", r.getEither()
        ));
    }

    private static Map<String, Object> toRow(Block b) {
        // Field names aligned with the frontend's BlockedUser type:
        // `profileImageUrl` (not blockedProfileImageUrl), and a string
        // `createdAt` parseable by `new Date(...)`.
        return Map.of(
                "id", b.getId(),
                "blockerId", b.getBlockerId(),
                "blockedId", b.getBlockedId(),
                "blockedNickname", b.getBlockedNickname(),
                "profileImageUrl", b.getBlockedProfileImageUrl(),
                "createdAt", java.time.Instant.ofEpochMilli(b.getCreatedAtMillis()).toString()
        );
    }

    /** So open group settings / group chat can refresh blocked-by-me / blocks-me flags. */
    private void notifyBlockStatusChanged(long a, long b) {
        stomp.convertAndSendToUser(String.valueOf(a), "/queue/friends", Map.of(
                "type", "BLOCK_STATUS_CHANGED",
                "userId", b
        ));
        stomp.convertAndSendToUser(String.valueOf(b), "/queue/friends", Map.of(
                "type", "BLOCK_STATUS_CHANGED",
                "userId", a
        ));
    }

    private static Map<String, Object> summary(UserProfile p) {
        return Map.of(
                "id", p.getId(),
                "email", p.getEmail(),
                "nickname", p.getNickname(),
                "name", p.getName(),
                "surname", p.getSurname(),
                "profileImageUrl", p.getProfileImageUrl()
        );
    }
}
