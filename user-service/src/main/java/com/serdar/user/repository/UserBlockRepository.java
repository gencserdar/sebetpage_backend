package com.serdar.user.repository;

import com.serdar.user.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    List<UserBlock> findAllByBlockerId(Long blockerId);

    @Query("SELECT b.blockerId FROM UserBlock b WHERE b.blockedId = :uid")
    List<Long> findAllBlockerIdsOf(@Param("uid") Long uid);

    @Modifying
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
