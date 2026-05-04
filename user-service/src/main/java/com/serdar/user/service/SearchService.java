package com.serdar.user.service;

import com.serdar.user.entity.UserProfile;
import com.serdar.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User half of the monolith's SearchService. Callers are filtered out of their
 * own results, as are users who have blocked them.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserProfileRepository users;
    private final BlockService blockService;
    private final FriendService friendService;

    public record UserSearchResult(UserProfile profile, int mutualCount) {}

    public List<UserSearchResult> searchUsers(long callerId, String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        Set<Long> exclusions = blockService.whoBlocksMe(callerId).stream().collect(Collectors.toSet());
        exclusions.add(callerId);
        // Also filter out people the caller blocked.
        blockService.myBlocks(callerId).forEach(b -> exclusions.add(b.getBlockedId()));

        Set<Long> myFriends = Set.copyOf(friendService.listFriendIds(callerId));

        return users.search(keyword).stream()
                .filter(u -> !exclusions.contains(u.getId()))
                .map(u -> {
                    Set<Long> theirFriends = Set.copyOf(friendService.listFriendIds(u.getId()));
                    int mutual = (int) theirFriends.stream().filter(myFriends::contains).count();
                    return new UserSearchResult(u, mutual);
                })
                .toList();
    }
}
