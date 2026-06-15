package com.serdar.user.service;

import com.serdar.user.client.AuthClient;
import com.serdar.user.entity.UserProfile;
import com.serdar.user.repository.UserProfileRepository;
import com.serdar.user.search.UserSearchIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserProfileRepository users;
    private final BlockService blockService;
    private final FriendService friendService;
    private final AuthClient authClient;
    private final UserSearchIndexService searchIndex;

    public record UserSearchResult(UserProfile profile, int mutualCount) {}

    public List<UserSearchResult> searchUsers(long callerId, String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();

        Set<Long> exclusions = new HashSet<>(blockService.whoBlocksMe(callerId));
        exclusions.add(callerId);
        blockService.blockedByMeIdSet(callerId).forEach(exclusions::add);

        Set<Long> myFriends = Set.copyOf(friendService.listFriendIds(callerId));

        List<Long> ids;
        try {
            ids = searchIndex.searchIds(keyword, 50);
        } catch (Exception e) {
            return users.search(keyword).stream()
                    .filter(u -> !exclusions.contains(u.getId()))
                    .filter(u -> !authClient.isFrozen(u.getId()))
                    .map(u -> toResult(u, myFriends))
                    .toList();
        }

        return ids.stream()
                .filter(id -> !exclusions.contains(id))
                .map(users::findById)
                .flatMap(java.util.Optional::stream)
                .filter(u -> !authClient.isFrozen(u.getId()))
                .map(u -> toResult(u, myFriends))
                .collect(Collectors.toList());
    }

    private UserSearchResult toResult(UserProfile u, Set<Long> myFriends) {
        Set<Long> theirFriends = Set.copyOf(friendService.listFriendIds(u.getId()));
        int mutual = (int) theirFriends.stream().filter(myFriends::contains).count();
        return new UserSearchResult(u, mutual);
    }
}
