package com.serdar.user.repository;

import com.serdar.user.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("SELECT f FROM Friendship f WHERE (f.user1Id = :a AND f.user2Id = :b) OR (f.user1Id = :b AND f.user2Id = :a)")
    Optional<Friendship> findByUsers(@Param("a") Long a, @Param("b") Long b);

    @Query("SELECT f FROM Friendship f WHERE f.user1Id = :uid OR f.user2Id = :uid")
    List<Friendship> findFriendshipsOfUser(@Param("uid") Long uid);

    boolean existsByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    @Modifying
    @Query("DELETE FROM Friendship f WHERE f.user1Id = :uid OR f.user2Id = :uid")
    void deleteAllForUser(@Param("uid") Long uid);
}
