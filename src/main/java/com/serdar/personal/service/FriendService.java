package com.serdar.personal.service;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.Friendship;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.UserDTO;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.repository.FriendshipRepository;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final UserContextService userContextService;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final  FriendshipRepository friendshipRepository;
    private final UserService userService;

    public Map<String, Object> getFriendStatus(String otherNickname) {
        User currentUser = userContextService.getCurrentUser();
        User otherUser = userRepository.findByNickname(otherNickname)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUser.equals(otherUser)) {
            return Map.of("status", "self");
        }

        if (areAlreadyFriends(currentUser, otherUser)) {
            return Map.of("status", "friends");
        }

        if (friendRequestRepository.existsByFromUserAndToUser(currentUser, otherUser)) {
            return Map.of("status", "sent");
        }

        if (friendRequestRepository.existsByFromUserAndToUser(otherUser, currentUser)) {
            Long requestId = friendRequestRepository
                    .findByFromUserAndToUser(otherUser, currentUser)
                    .map(FriendRequest::getId)
                    .orElse(null);

            return Map.of(
                    "status", "received",
                    "requestId", requestId
            );
        }

        return Map.of("status", "none");
    }

    public boolean areAlreadyFriends(User a, User b) {
        return friendshipRepository.existsByUser1AndUser2(a, b)
                || friendshipRepository.existsByUser1AndUser2(b, a);
    }

    public void removeFriend(Long userId1, Long userId2) {
        Optional<Friendship> friendshipOpt = friendshipRepository
                .findByUsers(userId1, userId2);

        friendshipOpt.ifPresent(friendshipRepository::delete);
    }

    public List<UserDTO> getFriends(User currentUser) {
        List<Friendship> friendships = friendshipRepository.findFriendshipsOfUser(currentUser.getId());

        return friendships.stream()
                .map(friendship -> {
                    User friend = friendship.getUser1().equals(currentUser)
                            ? friendship.getUser2()
                            : friendship.getUser1();
                    return userService.toDTO(friend);
                })
                .collect(Collectors.toList());
    }

}
