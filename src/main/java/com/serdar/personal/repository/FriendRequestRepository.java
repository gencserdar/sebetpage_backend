package com.serdar.personal.repository;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {
    boolean existsByFromUserAndToUser(User fromUser, User toUser);
    List<FriendRequest> findByToUserAndStatus(User toUser, FriendRequest.RequestStatus status);
    List<FriendRequest> findByFromUserAndStatus(User fromUser, FriendRequest.RequestStatus status);
    Optional<FriendRequest> findByFromUserAndToUser(User fromUser, User toUser);

}
