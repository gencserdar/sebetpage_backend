package com.serdar.personal.service;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.Friendship;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.repository.FriendshipRepository;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserContextService userContextService;
    private final FriendsService friendsService;

    public void sendFriendRequest(String toNickname) {
        User fromUser = userContextService.getCurrentUser();
        User toUser = userRepository.findByNickname(toNickname)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (fromUser.equals(toUser)) {
            throw new IllegalArgumentException("You can't send request to yourself.");
        }

        // Zaten arkadaş mı?
        if (friendsService.areAlreadyFriends(fromUser, toUser)) {
            throw new IllegalStateException("You are already friends.");
        }

        // Ters yönlü istek var mı? (B → A)
        boolean reverseExists = friendRequestRepository.existsByFromUserAndToUser(toUser, fromUser);
        if (reverseExists) {
            createFriendship(toUser, fromUser);
        }

        // Zaten istek atılmış mı?
        boolean exists = friendRequestRepository.existsByFromUserAndToUser(fromUser, toUser);
        if (exists) {
            throw new IllegalStateException("Request already sent.");
        }


        FriendRequest request = FriendRequest.builder()
                .fromUser(fromUser)
                .toUser(toUser)
                .status(FriendRequest.RequestStatus.PENDING)
                .sentAt(LocalDateTime.now())
                .build();

        friendRequestRepository.save(request);
    }

    public List<FriendRequest> getIncomingRequests() {
        User currentUser = userContextService.getCurrentUser();
        return friendRequestRepository.findByToUserAndStatus(currentUser, FriendRequest.RequestStatus.PENDING);
    }

    public List<FriendRequest> getOutgoingRequests() {
        User currentUser = userContextService.getCurrentUser();
        return friendRequestRepository.findByFromUserAndStatus(currentUser, FriendRequest.RequestStatus.PENDING);
    }

    public void respondToRequest(Long requestId, boolean accept) {
        User currentUser = userContextService.getCurrentUser();

        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found."));

        if (!request.getToUser().equals(currentUser)) {
            throw new RuntimeException("Not authorized to respond to this request.");
        }

        if (request.getStatus() != FriendRequest.RequestStatus.PENDING) {
            throw new RuntimeException("Request already responded.");
        }

        if (accept) {
            if (!friendsService.areAlreadyFriends(request.getFromUser(), request.getToUser())) {
                createFriendship(request.getFromUser(), request.getToUser());
            }
            friendRequestRepository.delete(request);
        } else {
            friendRequestRepository.delete(request);
        }

        friendRequestRepository.save(request);
    }

    // Yardımcı fonksiyonlar
    private void createFriendship(User a, User b) {
        User user1 = a.getId() < b.getId() ? a : b;
        User user2 = a.getId() < b.getId() ? b : a;

        Friendship friendship = Friendship.builder()
                .user1(user1)
                .user2(user2)
                .createdAt(LocalDateTime.now())
                .build();

        friendshipRepository.save(friendship);
    }
}
