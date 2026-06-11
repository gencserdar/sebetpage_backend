package com.serdar.gateway.controller;

import com.serdar.gateway.client.CommunityClient;
import com.serdar.gateway.client.UserClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.community.Community;
import com.serdar.proto.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Combined search — gateway fan-outs to user-service and community-service,
 * then merges the results into a single payload shaped the way the frontend expects.
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final UserClient users;
    private final CommunityClient communities;

    @GetMapping
    public ResponseEntity<?> search(@RequestParam String keyword) {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> userRows = users.searchUsers(me, keyword).getUsersList()
                .stream().map(SearchController::toUser).toList();
        List<Map<String, Object>> communityRows = communities.search(keyword).getCommunitiesList()
                .stream().map(SearchController::toCommunity).toList();
        return ResponseEntity.ok(Map.of("users", userRows, "communities", communityRows));
    }

    private static Map<String, Object> toUser(UserSummary u) {
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

    private static Map<String, Object> toCommunity(Community c) {
        return Map.of(
                "type", "COMMUNITY",
                "id", c.getId(),
                "name", c.getName(),
                "description", c.getDescription(),
                "isPrivate", c.getIsPrivate(),
                "createdBy", c.getCreatedBy(),
                "createdAtMillis", c.getCreatedAtMillis(),
                "mutualFriendCount", 0
        );
    }
}
