package com.serdar.personal.service;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.Friendship;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.repository.FriendshipRepository;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendRequestService {

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserContextService userContextService;
    private final FriendService friendService;
    private final FriendWebSocketService friendWebSocketService;
    private final BlockService blockService;

    /* ====================== SEND ====================== */

    @Transactional
    public Long sendFriendRequest(String toNickname) {
        User fromUser = userContextService.getCurrentUser();
        User toUser = userRepository.findByNickname(toNickname)
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (fromUser.equals(toUser)) {
            throw new IllegalArgumentException("You can't send request to yourself.");
        }

        // Block kontrolü (iki yön)
        if (blockService.isBlockedEitherWay(fromUser.getId(), toUser.getId())) {
            throw new RuntimeException("Friend request not allowed due to a block.");
        }

        // Zaten arkadaşlar mı?
        if (friendService.areAlreadyFriends(fromUser, toUser)) {
            throw new IllegalStateException("You are already friends.");
        }

        // Ters yönlü istek var mı? (toUser -> fromUser)
        boolean reverseExists = friendRequestRepository.existsByFromUserAndToUser(toUser, fromUser);
        if (reverseExists) {
            // Ters taraftaki PENDING isteği çek
            FriendRequest reverse = friendRequestRepository
                    .findByFromUserAndToUser(toUser, fromUser)
                    .orElse(null);

            // Son kez block doğrula
            if (blockService.isBlockedEitherWay(fromUser.getId(), toUser.getId())) {
                throw new RuntimeException("Friend request not allowed due to a block.");
            }

            // Arkadaşlığı oluştur
            if (!friendService.areAlreadyFriends(fromUser, toUser)) {
                createFriendship(toUser, fromUser);
            }

            // Reverse isteği sil ve WS event gönder
            if (reverse != null) {
                friendRequestRepository.delete(reverse);
                friendWebSocketService.sendFriendRequestAccepted(reverse);
            }

            // Her iki tarafa friend eklendi event'i
            friendWebSocketService.sendFriendAdded(fromUser, toUser);
            return null; // No outgoing request created - became friends directly
        }

        // Aynı yönde zaten istek atılmış mı?
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

        FriendRequest saved = friendRequestRepository.save(request);
        friendWebSocketService.sendFriendRequestReceived(saved);

        return saved.getId(); // Return the request ID for potential cancellation
    }

    /* ====================== LISTS ====================== */

    @Transactional(readOnly = true)
    public List<FriendRequest> getIncomingRequests() {
        User me = userContextService.getCurrentUser();
        return friendRequestRepository.findByToUserAndStatus(me, FriendRequest.RequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<FriendRequest> getOutgoingRequests() {
        User me = userContextService.getCurrentUser();
        return friendRequestRepository.findByFromUserAndStatus(me, FriendRequest.RequestStatus.PENDING);
    }

    /* ====================== RESPOND ====================== */

    @Transactional
    public void respondToRequest(Long requestId, boolean accept) {
        User me = userContextService.getCurrentUser();

        FriendRequest req = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found."));

        if (!req.getToUser().equals(me)) {
            throw new RuntimeException("Not authorized to respond to this request.");
        }

        if (req.getStatus() != FriendRequest.RequestStatus.PENDING) {
            throw new RuntimeException("Request already responded.");
        }

        // Yanıt anında block kontrolü
        if (blockService.isBlockedEitherWay(req.getFromUser().getId(), req.getToUser().getId())) {
            friendWebSocketService.sendFriendRequestRejected(req);
            friendRequestRepository.delete(req);
            return;
        }

        if (accept) {
            if (!friendService.areAlreadyFriends(req.getFromUser(), req.getToUser())) {
                createFriendship(req.getFromUser(), req.getToUser());
            }

            friendWebSocketService.sendFriendRequestAccepted(req);
            friendWebSocketService.sendFriendAdded(req.getFromUser(), req.getToUser());

            friendRequestRepository.delete(req);
        } else {
            friendWebSocketService.sendFriendRequestRejected(req);
            friendRequestRepository.delete(req);
        }
    }

    /* ====================== CANCEL (new) ====================== */

    /** ID ile outgoing friend request iptali (idempotent) */
    public void cancelOutgoingRequest(Long requestId) {
        Optional<FriendRequest> requestOpt = friendRequestRepository.findById(requestId);
        if (requestOpt.isPresent()) {
            FriendRequest request = requestOpt.get();
            User fromUser = request.getFromUser();
            User toUser = request.getToUser(); // This is who needs to be notified

            // Delete the request
            friendRequestRepository.delete(request);

            // Send WebSocket notification to the recipient
            friendWebSocketService.sendFriendRequestCancelled(toUser, request);
        }
    }

    /* ====================== HELPERS ====================== */

    @Transactional
    protected void createFriendship(User a, User b) {
        // (user1, user2) sıralı sakla (unique constraint için iyi)
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
