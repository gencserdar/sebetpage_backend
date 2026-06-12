package com.serdar.user.service;

import com.serdar.common.ServiceException;
import com.serdar.user.client.AuthClient;
import com.serdar.user.entity.FriendRequest;
import com.serdar.user.entity.Friendship;
import com.serdar.user.entity.UserProfile;
import com.serdar.user.repository.FriendRequestRepository;
import com.serdar.user.repository.FriendshipRepository;
import com.serdar.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Friendship + friend-request logic — preserves the monolith's semantics:
 * - a reverse pending request auto-accepts on send
 * - blocking either direction blocks new requests
 * - friendships are stored with canonical (u1 < u2) ids
 */
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final UserProfileRepository users;
    private final BlockService blockService;
    private final AuthClient authClient;

    // --- friendships --------------------------------------------------------

    public boolean areFriends(long a, long b) {
        return friendships.findByUsers(a, b).isPresent();
    }

    public List<UserProfile> listFriends(long userId) {
        List<Friendship> list = friendships.findFriendshipsOfUser(userId);
        return list.stream()
                .map(f -> f.getUser1Id().equals(userId) ? f.getUser2Id() : f.getUser1Id())
                .filter(id -> !authClient.isFrozen(id))
                .map(id -> users.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<Long> listFriendIds(long userId) {
        return friendships.findFriendshipsOfUser(userId).stream()
                .map(f -> f.getUser1Id().equals(userId) ? f.getUser2Id() : f.getUser1Id())
                .filter(id -> !authClient.isFrozen(id))
                .toList();
    }

    @Transactional
    public void removeFriend(long a, long b) {
        friendships.findByUsers(a, b).ifPresent(friendships::delete);
    }

    public Map<String, Object> friendStatus(long callerId, String otherNickname) {
        UserProfile other = users.findByNickname(otherNickname)
                .orElseThrow(() -> ServiceException.notFound("User not found"));
        if (other.getId() != callerId && authClient.isFrozen(other.getId())) {
            throw ServiceException.notFound("User not found");
        }
        if (other.getId() == callerId) return Map.of("status", "self", "otherUserId", other.getId());

        if (areFriends(callerId, other.getId()))
            return Map.of("status", "friends", "otherUserId", other.getId());

        Optional<FriendRequest> sent =
                requests.findByFromUserIdAndToUserIdAndStatus(callerId, other.getId(), FriendRequest.Status.PENDING);
        if (sent.isPresent()) return Map.of("status", "sent", "requestId", sent.get().getId(), "otherUserId", other.getId());

        Optional<FriendRequest> received =
                requests.findByFromUserIdAndToUserIdAndStatus(other.getId(), callerId, FriendRequest.Status.PENDING);
        if (received.isPresent()) return Map.of("status", "received", "requestId", received.get().getId(), "otherUserId", other.getId());

        return Map.of("status", "none", "otherUserId", other.getId());
    }

    // --- requests -----------------------------------------------------------

    /** @return map with status + optional requestId + toUserId */
    @Transactional
    public Map<String, Object> sendRequest(long fromId, String toNickname) {
        if (authClient.isFrozen(fromId)) {
            throw ServiceException.forbidden("Account frozen");
        }
        UserProfile to = users.findByNickname(toNickname)
                .orElseThrow(() -> ServiceException.notFound("User not found"));
        long toId = to.getId();
        if (authClient.isFrozen(toId)) {
            throw ServiceException.notFound("User not found");
        }
        if (fromId == toId) throw ServiceException.invalid("You can't send a request to yourself");
        if (blockService.eitherWay(fromId, toId)) throw ServiceException.forbidden("Blocked");
        if (areFriends(fromId, toId)) throw ServiceException.conflict("Already friends");

        // Reverse pending? auto-accept.
        Optional<FriendRequest> reverse =
                requests.findByFromUserIdAndToUserIdAndStatus(toId, fromId, FriendRequest.Status.PENDING);
        if (reverse.isPresent()) {
            createFriendshipIfMissing(fromId, toId);
            requests.delete(reverse.get());
            return Map.of("status", "friends", "toUserId", toId);
        }

        // Already-outgoing?
        if (requests.existsByFromUserIdAndToUserIdAndStatus(fromId, toId, FriendRequest.Status.PENDING))
            throw ServiceException.conflict("Request already sent");

        FriendRequest req = requests.save(FriendRequest.builder()
                .fromUserId(fromId)
                .toUserId(toId)
                .status(FriendRequest.Status.PENDING)
                .sentAt(LocalDateTime.now())
                .build());
        return Map.of("status", "sent", "requestId", req.getId(), "toUserId", toId);
    }

    public List<FriendRequest> incoming(long userId) {
        return requests.findByToUserIdAndStatus(userId, FriendRequest.Status.PENDING).stream()
                .filter(r -> !authClient.isFrozen(r.getFromUserId()))
                .toList();
    }

    public List<FriendRequest> outgoing(long userId) {
        return requests.findByFromUserIdAndStatus(userId, FriendRequest.Status.PENDING).stream()
                .filter(r -> !authClient.isFrozen(r.getToUserId()))
                .toList();
    }

    @Transactional
    public void respondToRequest(long requestId, long responderId, boolean accept) {
        FriendRequest req = requests.findById(requestId)
                .orElseThrow(() -> ServiceException.notFound("Request not found"));
        if (!req.getToUserId().equals(responderId))
            throw ServiceException.forbidden("Not your request to respond to");
        if (req.getStatus() != FriendRequest.Status.PENDING)
            throw ServiceException.precondition("Already responded");

        if (blockService.eitherWay(req.getFromUserId(), req.getToUserId())) {
            requests.delete(req);
            return;
        }
        if (accept) createFriendshipIfMissing(req.getFromUserId(), req.getToUserId());
        requests.delete(req);
    }

    @Transactional
    public void cancelRequest(long requestId, long cancellerId) {
        requests.findById(requestId).ifPresent(req -> {
            if (!req.getFromUserId().equals(cancellerId))
                throw ServiceException.forbidden("Not your request to cancel");
            requests.delete(req);
        });
    }

    // --- helpers ------------------------------------------------------------

    private void createFriendshipIfMissing(long a, long b) {
        if (areFriends(a, b)) return;
        long u1 = Math.min(a, b), u2 = Math.max(a, b);
        friendships.save(Friendship.builder()
                .user1Id(u1)
                .user2Id(u2)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public UserProfile fetch(long id) {
        return users.findById(id).orElseThrow(() -> ServiceException.notFound("User not found"));
    }
}
