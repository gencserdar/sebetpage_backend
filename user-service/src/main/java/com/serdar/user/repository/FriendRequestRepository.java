package com.serdar.user.repository;

import com.serdar.user.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    Optional<FriendRequest> findByFromUserIdAndToUserIdAndStatus(Long from, Long to, FriendRequest.Status status);
    boolean existsByFromUserIdAndToUserIdAndStatus(Long from, Long to, FriendRequest.Status status);
    List<FriendRequest> findByToUserIdAndStatus(Long toUserId, FriendRequest.Status status);
    List<FriendRequest> findByFromUserIdAndStatus(Long fromUserId, FriendRequest.Status status);
}
