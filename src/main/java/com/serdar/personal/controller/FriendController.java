package com.serdar.personal.controller;

import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.UserDTO;
import com.serdar.personal.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping("/status/{nickname}")
    public ResponseEntity<?> getFriendStatus(@PathVariable String nickname) {
        Map<String, Object> result = friendService.getFriendStatus(nickname);
        return ResponseEntity.ok(result);
    }


    @DeleteMapping("/remove/{targetUserId}")
    public ResponseEntity<?> removeFriend(@PathVariable Long targetUserId,
                                          @AuthenticationPrincipal User currentUser) {
        friendService.removeFriend(currentUser.getId(), targetUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getFriends(@AuthenticationPrincipal User currentUser) {
        List<UserDTO> friends = friendService.getFriends(currentUser);
        return ResponseEntity.ok(friends);
    }


}
