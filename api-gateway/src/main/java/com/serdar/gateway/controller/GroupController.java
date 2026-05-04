package com.serdar.gateway.controller;

import com.serdar.gateway.client.GroupClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.group.Group;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupClient groups;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        long me = CurrentUser.require().id();
        Group g = groups.createGroup(me, body.getOrDefault("name", ""), body.getOrDefault("description", ""));
        return ResponseEntity.ok(toRow(g));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine() {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> rows = groups.mine(me).getGroupsList().stream().map(GroupController::toRow).toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{groupId}/invite")
    public ResponseEntity<?> invite(@PathVariable long groupId, @RequestParam long toUserId) {
        long me = CurrentUser.require().id();
        groups.invite(groupId, me, toUserId);
        return ResponseEntity.ok("Invited");
    }

    @PostMapping("/invites/{inviteId}/respond")
    public ResponseEntity<?> respond(@PathVariable long inviteId, @RequestParam boolean accept) {
        long me = CurrentUser.require().id();
        groups.respond(inviteId, me, accept);
        return ResponseEntity.ok(accept ? "Joined" : "Declined");
    }

    private static Map<String, Object> toRow(Group g) {
        return Map.of(
                "id", g.getId(),
                "name", g.getName(),
                "description", g.getDescription(),
                "isPrivate", g.getIsPrivate(),
                "createdBy", g.getCreatedBy(),
                "createdAtMillis", g.getCreatedAtMillis(),
                "myRole", g.getMyRole().name()
        );
    }
}
