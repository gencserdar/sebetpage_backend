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
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final FriendWebSocketService friendWebSocketService;

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

        // Check for outgoing request (sent by current user)
        Optional<FriendRequest> outgoingRequest = friendRequestRepository
                .findByFromUserAndToUser(currentUser, otherUser);
        if (outgoingRequest.isPresent()) {
            return Map.of("status", "sent", "requestId", outgoingRequest.get().getId());
        }

        // Check for incoming request (sent to current user)
        Optional<FriendRequest> incomingRequest = friendRequestRepository
                .findByFromUserAndToUser(otherUser, currentUser);
        if (incomingRequest.isPresent()) {
            return Map.of("status", "received", "requestId", incomingRequest.get().getId());
        }

        return Map.of("status", "none");
    }

    public boolean areAlreadyFriends(User a, User b) {
        return friendshipRepository.existsByUser1AndUser2(a, b)
                || friendshipRepository.existsByUser1AndUser2(b, a);
    }

    public void removeFriend(Long userId1, Long userId2) {
        Optional<Friendship> friendshipOpt = friendshipRepository.findByUsers(userId1, userId2);
        if (friendshipOpt.isPresent()) {
            Friendship friendship = friendshipOpt.get();
            User user1 = friendship.getUser1();
            User user2 = friendship.getUser2();
            friendshipRepository.delete(friendship);
            // canlı bildirim
            friendWebSocketService.sendFriendRemoved(user1, user2);
        }
    }

    /** UI için DTO listesi (eski davranış: presence eklemiyoruz) */
    public List<UserDTO> getFriends(User currentUser) {
        List<Friendship> friendships = friendshipRepository.findFriendshipsOfUser(currentUser.getId());
        return friendships.stream()
                .map(f -> f.getUser1().equals(currentUser) ? f.getUser2() : f.getUser1())
                .map(userService::toDTO)
                .collect(Collectors.toList());
    }

    /* =========================
       YENİ: Presence için yardımcılar
       ========================= */

    /** Kullanıcının arkadaş ID’leri (presence/snapshot için hızlı yol) */
    public List<Long> getFriendIdsOf(Long userId) {
        return friendshipRepository.findFriendshipsOfUser(userId).stream()
                .map(f -> f.getUser1().getId().equals(userId) ? f.getUser2().getId() : f.getUser1().getId())
                .toList();
    }

    /** Presence yayınlarında tüm friend entity’leri lazımsa */
    public List<User> getFriendEntitiesOf(Long userId) {
        List<Long> ids = getFriendIdsOf(userId);
        return ids.isEmpty() ? List.of() : userRepository.findAllById(ids);
    }
}
