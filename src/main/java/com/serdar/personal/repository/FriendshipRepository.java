package com.serdar.personal.repository;

import com.serdar.personal.model.Friendship;
import com.serdar.personal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUser1OrUser2(User user1, User user2);
    boolean existsByUser1AndUser2(User user1, User user2);
    List<Friendship> findByUser1_IdOrUser2_Id(Long user1Id, Long user2Id);
}
