package com.serdar.personal.controller;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.FriendStatusResponse;
import com.serdar.personal.service.FriendRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friend-request")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    @PostMapping("/send/{nickname}")
    public ResponseEntity<String> sendFriendRequest(@PathVariable String nickname) {
        friendRequestService.sendFriendRequest(nickname);
        return ResponseEntity.ok("Friend request sent.");
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
}
