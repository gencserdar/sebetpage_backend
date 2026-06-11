package com.serdar.gateway.controller;

import com.serdar.gateway.client.CommunityClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.community.Community;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityClient communities;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        long me = CurrentUser.require().id();
        Community c = communities.createCommunity(me, body.getOrDefault("name", ""), body.getOrDefault("description", ""));
        return ResponseEntity.ok(toRow(c));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine() {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> rows = communities.mine(me).getCommunitiesList().stream()
                .map(CommunityController::toRow).toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{communityId}/invite")
    public ResponseEntity<?> invite(@PathVariable long communityId, @RequestParam long toUserId) {
        long me = CurrentUser.require().id();
        communities.invite(communityId, me, toUserId);
        return ResponseEntity.ok("Invited");
    }

    @PostMapping("/invites/{inviteId}/respond")
    public ResponseEntity<?> respond(@PathVariable long inviteId, @RequestParam boolean accept) {
        long me = CurrentUser.require().id();
        communities.respond(inviteId, me, accept);
        return ResponseEntity.ok(accept ? "Joined" : "Declined");
    }

    private static Map<String, Object> toRow(Community c) {
        return Map.of(
                "id", c.getId(),
                "name", c.getName(),
                "description", c.getDescription(),
                "isPrivate", c.getIsPrivate(),
                "createdBy", c.getCreatedBy(),
                "createdAtMillis", c.getCreatedAtMillis(),
                "myRole", c.getMyRole().name()
        );
    }
}
