package com.serdar.personal.controller;

import com.serdar.personal.model.dto.FriendStatusResponse;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.service.FriendRequestService;
import com.serdar.personal.service.FriendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendsController {

    private final FriendsService friendsService;
    @GetMapping("/status/{nickname}")
    public ResponseEntity<FriendStatusResponse> getFriendStatus(@PathVariable String nickname) {
        String status = friendsService.getFriendshipStatus(nickname);
        return ResponseEntity.ok(new FriendStatusResponse(status));
    }
}
