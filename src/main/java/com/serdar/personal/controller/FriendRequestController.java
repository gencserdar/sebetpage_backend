package com.serdar.personal.controller;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.FriendStatusResponse;
import com.serdar.personal.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friend-request")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @PostMapping("/send/{nickname}")
    public ResponseEntity<Map<String, Object>> sendFriendRequest(@PathVariable String nickname) {
        Long requestId = friendRequestService.sendFriendRequest(nickname);

        Map<String, Object> response = new HashMap<>();
        if (requestId != null) {
            response.put("status", "sent");
            response.put("requestId", requestId);
        } else {
            response.put("status", "friends"); // Auto-accepted due to reverse request
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<FriendRequest>> getIncomingRequests() {
        return ResponseEntity.ok(friendRequestService.getIncomingRequests());
    }

    @GetMapping("/outgoing")
    public ResponseEntity<List<FriendRequest>> getOutgoingRequests() {
        return ResponseEntity.ok(friendRequestService.getOutgoingRequests());
    }

    @PostMapping("/respond")
    public ResponseEntity<String> respondToRequest(
            @RequestParam Long requestId,
            @RequestParam boolean accept
    ) {
        friendRequestService.respondToRequest(requestId, accept);
        return ResponseEntity.ok("Friend request " + (accept ? "accepted" : "rejected") + ".");
    }

    @DeleteMapping("/cancel/{requestId}")
    public ResponseEntity<String> cancelOutgoingRequest(@PathVariable Long requestId) {
        friendRequestService.cancelOutgoingRequest(requestId);
        return ResponseEntity.ok("Friend request canceled.");
    }
}
