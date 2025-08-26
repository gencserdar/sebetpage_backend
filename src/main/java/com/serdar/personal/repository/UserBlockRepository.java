package com.serdar.personal.repository;

import com.serdar.personal.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// repository/UserBlockRepository.java
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
    boolean existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(Long a, Long b, Long b2, Long a2);
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);


    @Query("select ub.blocker.id from UserBlock ub where ub.blocked.id = :blockedId")
    List<Long> findAllBlockerIdsOf(@Param("blockedId") Long blockedId);

    // UserBlockRepository.java
    List<UserBlock> findAllByBlockerId(Long blockerId);
}
