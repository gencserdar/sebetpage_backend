package com.serdar.personal.service;

import com.serdar.personal.model.User;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.repository.FriendshipRepository;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FriendsService {
    private final UserContextService userContextService;
    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final  FriendshipRepository friendshipRepository;

    public String getFriendshipStatus(String otherNickname) {
        User currentUser = userContextService.getCurrentUser();
        User otherUser = userRepository.findByNickname(otherNickname)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (currentUser.equals(otherUser)) {
            return "self";
        }

        if (areAlreadyFriends(currentUser, otherUser)) {
            return "friends";
        }

        if (friendRequestRepository.existsByFromUserAndToUser(currentUser, otherUser)) {
            return "sent";
        }

        if (friendRequestRepository.existsByFromUserAndToUser(otherUser, currentUser)) {
            return "received";
        }

        return "none";
    }

    public boolean areAlreadyFriends(User a, User b) {
        return friendshipRepository.existsByUser1AndUser2(a, b)
                || friendshipRepository.existsByUser1AndUser2(b, a);
    }
}
