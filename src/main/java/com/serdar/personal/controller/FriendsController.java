package com.serdar.personal.controller;

import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.FriendStatusResponse;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.service.FriendRequestService;
import com.serdar.personal.service.FriendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendsController {

    private final FriendsService friendsService;

    @GetMapping("/status/{nickname}")
    public ResponseEntity<?> getFriendStatus(@PathVariable String nickname) {
        Map<String, Object> result = friendsService.getFriendStatus(nickname);
        return ResponseEntity.ok(result);
    }


    @DeleteMapping("/remove/{targetUserId}")
    public ResponseEntity<?> removeFriend(@PathVariable Long targetUserId,
                                          @AuthenticationPrincipal User currentUser) {
        friendsService.removeFriend(currentUser.getId(), targetUserId);
        return ResponseEntity.ok().build();
    }

}
