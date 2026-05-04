package com.serdar.gateway.controller;

import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.user.FriendStatusResponse;
import com.serdar.proto.user.UserList;
import com.serdar.proto.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final UserClient users;
    private final SimpMessagingTemplate stomp;

    // Both /api/friends and /api/friends/list return the friend list — the
    // frontend's FriendsList uses the base path, but older code paths may
    // still hit /list. Keep both registered to avoid another round-trip of
    // surprises.
    @GetMapping({"", "/list"})
    public ResponseEntity<?> list() {
        long me = CurrentUser.require().id();
        UserList list = users.listFriends(me);
        List<Map<String, Object>> rows = list.getUsersList().stream().map(FriendController::toRow).toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/status/{nickname}")
    public ResponseEntity<?> status(@PathVariable String nickname) {
        long me = CurrentUser.require().id();
        FriendStatusResponse r = users.friendStatus(me, nickname);
        return ResponseEntity.ok(Map.of(
                "status", r.getStatus(),
                "requestId", r.getRequestId(),
                "otherUserId", r.getOtherUserId()
        ));
    }

    @DeleteMapping("/remove/{userId}")
    public ResponseEntity<?> remove(@PathVariable long userId) {
        long me = CurrentUser.require().id();

        // Resolve both profiles BEFORE the delete — once user-service tears
        // down the friendship row, FriendChat needs the other side's nickname
        // and email to identify which open chat tab to disable.
        UserProfile myProfile;
        UserProfile otherProfile;
        try {
            myProfile = users.byId(me);
            otherProfile = users.byId(userId);
        } catch (Exception e) {
            myProfile = null;
            otherProfile = null;
        }

        users.removeFriend(me, userId);

        // FriendsList listens for FRIEND_REMOVED to trigger a refresh, and
        // FriendChat checks `event.removedFriend.{email,nickname}` to decide
        // whether the currently-open chat tab should be locked down.
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
        return ResponseEntity.ok("Friend removed");
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

    private static Map<String, Object> toRow(UserProfile p) {
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
