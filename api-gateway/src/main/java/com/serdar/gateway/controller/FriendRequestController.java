package com.serdar.gateway.controller;

import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.ws.WsBridgeService;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.user.FriendRequest;
import com.serdar.proto.user.FriendRequestList;
import com.serdar.proto.user.SendFriendRequestResponse;
import com.serdar.proto.user.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Friend-request REST endpoints. Each mutating call also fans out a STOMP
 * event to the affected users so the FriendRequestsDropdown / FriendsList
 * components can update without a polling refresh — that's the contract the
 * frontend code in the monolith relied on.
 *
 * The user-service itself doesn't know anything about WebSockets, so we emit
 * the events here in the gateway. The chat-service emits its own events
 * (presence, message fan-out) directly through the gRPC subscription stream.
 */
@Slf4j
@RestController
@RequestMapping("/api/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final UserClient users;
    private final SimpMessagingTemplate stomp;
    private final WsBridgeService wsBridge;

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestParam String toNickname) {
        long me = CurrentUser.require().id();
        SendFriendRequestResponse r = users.sendRequest(me, toNickname);

        // If the user-service auto-accepted (because there was a reverse pending
        // request), there's no new outgoing request to look up — both sides need
        // to refresh their friends panels instead.
        if ("FRIENDS".equalsIgnoreCase(r.getStatus()) || "ACCEPTED".equalsIgnoreCase(r.getStatus())) {
            notifyFriendAdded(me, r.getToUserId());
        } else {
            // Standard send — locate the freshly-created request in our outgoing
            // list and push the full row to the recipient so their dropdown can
            // render without a roundtrip.
            FriendRequest fresh = findById(users.outgoing(me), r.getRequestId());
            if (fresh != null) {
                Map<String, Object> row = toRow(fresh);
                stomp.convertAndSendToUser(String.valueOf(r.getToUserId()), "/queue/friends", Map.of(
                        "type", "FRIEND_REQUEST_RECEIVED",
                        "requestId", fresh.getId(),
                        "request", row
                ));
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", r.getStatus(),
                "requestId", r.getRequestId(),
                "toUserId", r.getToUserId()
        ));
    }

    @GetMapping("/incoming")
    public ResponseEntity<?> incoming() {
        long me = CurrentUser.require().id();
        return ResponseEntity.ok(toRows(users.incoming(me)));
    }

    @GetMapping("/outgoing")
    public ResponseEntity<?> outgoing() {
        long me = CurrentUser.require().id();
        return ResponseEntity.ok(toRows(users.outgoing(me)));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<?> respond(@PathVariable long id, @RequestParam boolean accept) {
        long me = CurrentUser.require().id();

        // Look up the request before we respond — once user-service deletes/updates
        // it we lose the fromUserId we need to address the event.
        FriendRequest req = findById(users.incoming(me), id);
        Long fromUserId = (req != null) ? req.getFromUserId() : null;

        users.respondToRequest(id, me, accept);

        if (fromUserId != null) {
            // Tell the original sender their request was accepted/rejected so
            // they can clear it from any UI that's still watching.
            stomp.convertAndSendToUser(String.valueOf(fromUserId), "/queue/friends", Map.of(
                    "type", accept ? "FRIEND_REQUEST_ACCEPTED" : "FRIEND_REQUEST_REJECTED",
                    "requestId", id
            ));
        }

        if (accept && fromUserId != null) {
            // Both sides need their friends list to refresh.
            notifyFriendAdded(me, fromUserId);
        }

        return ResponseEntity.ok(accept ? "Accepted" : "Rejected");
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable long id) {
        long me = CurrentUser.require().id();

        // Locate the outgoing request before we cancel so we know who to notify.
        FriendRequest req = findById(users.outgoing(me), id);
        Long toUserId = (req != null) ? req.getToUserId() : null;

        users.cancelRequest(id, me);

        if (toUserId != null) {
            stomp.convertAndSendToUser(String.valueOf(toUserId), "/queue/friends", Map.of(
                    "type", "FRIEND_REQUEST_CANCELLED",
                    "requestId", id,
                    "fromUserId", me,
                    "toUserId", toUserId
            ));
        }

        return ResponseEntity.ok("Cancelled");
    }

    /** Emit FRIEND_ADDED + REQUEST_ACCEPTED to both sides so each refreshes. */
    private void notifyFriendAdded(long a, long b) {
        stomp.convertAndSendToUser(String.valueOf(a), "/queue/friends", Map.of(
                "type", "FRIEND_ADDED",
                "userId", b
        ));
        stomp.convertAndSendToUser(String.valueOf(b), "/queue/friends", Map.of(
                "type", "FRIEND_ADDED",
                "userId", a
        ));
        // Also send REQUEST_ACCEPTED — FriendsList reloads on either, but the
        // FriendRequestsDropdown only clears on REQUEST_ACCEPTED-style events.
        stomp.convertAndSendToUser(String.valueOf(a), "/queue/friends", Map.of("type", "REQUEST_ACCEPTED"));
        stomp.convertAndSendToUser(String.valueOf(b), "/queue/friends", Map.of("type", "REQUEST_ACCEPTED"));
        wsBridge.replaySnapshot(a);
        wsBridge.replaySnapshot(b);
    }

    private static FriendRequest findById(FriendRequestList list, long id) {
        return list.getRequestsList().stream()
                .filter(r -> r.getId() == id)
                .findFirst()
                .orElse(null);
    }

    private static List<Map<String, Object>> toRows(FriendRequestList list) {
        return list.getRequestsList().stream().map(FriendRequestController::toRow).toList();
    }

    private static Map<String, Object> toRow(FriendRequest r) {
        return Map.of(
                "id", r.getId(),
                "fromUserId", r.getFromUserId(),
                "toUserId", r.getToUserId(),
                "status", r.getStatus(),
                "sentAtMillis", r.getSentAtMillis(),
                "fromUser", summary(r.getFromUser()),
                "toUser", summary(r.getToUser())
        );
    }

    private static Map<String, Object> summary(UserProfile p) {
        return Map.of(
                "id", p.getId(),
                "nickname", p.getNickname(),
                "name", p.getName(),
                "surname", p.getSurname(),
                "profileImageUrl", p.getProfileImageUrl()
        );
    }
}
