package com.serdar.gateway.controller;

import com.serdar.gateway.client.GroupClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.group.Group;
import com.serdar.proto.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Combined search — gateway fan-outs to user-service and group-service, then
 * merges the results into a single payload shaped the way the frontend expects.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final UserClient users;
    private final GroupClient groups;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String keyword) {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> userRows = users.searchUsers(me, keyword).getUsersList()
                .stream().map(SearchController::toUser).toList();
        List<Map<String, Object>> groupRows = groups.search(keyword).getGroupsList()
                .stream().map(SearchController::toGroup).toList();
        return ResponseEntity.ok(Map.of("users", userRows, "groups", groupRows));
    }

    private static Map<String, Object> toUser(UserSummary u) {
        // `type` is required — the frontend's click handler in SearchBar.tsx
        // branches on it to decide between /profile/:nickname and /group/:id.
        // Without it every user result navigated to /group/<userId>, silently
        // swallowing the profile popup.
        return Map.of(
                "type", "USER",
                "id", u.getId(),
                "nickname", u.getNickname(),
                "name", u.getName(),
                "surname", u.getSurname(),
                "profileImageUrl", u.getProfileImageUrl(),
                "mutualFriendCount", u.getMutualFriendCount()
        );
    }

    private static Map<String, Object> toGroup(Group g) {
        return Map.of(
                "type", "GROUP",
                "id", g.getId(),
                "name", g.getName(),
                "description", g.getDescription(),
                "isPrivate", g.getIsPrivate(),
                "createdBy", g.getCreatedBy(),
                "createdAtMillis", g.getCreatedAtMillis(),
                // Frontend's SearchResult type expects this on both kinds.
                "mutualFriendCount", 0
        );
    }
}
