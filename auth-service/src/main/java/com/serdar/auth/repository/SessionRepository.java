package com.serdar.auth.repository;

import com.serdar.auth.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByTokenHash(String tokenHash);

    List<Session> findAllByUserId(Long userId);

    /**
     * Bulk-delete all sessions for a user — used by logout-all and password reset/change.
     * Uses a JPQL DELETE so Spring doesn't have to load each entity first (avoids N+1).
     */
    @Modifying
    @Query("DELETE FROM Session s WHERE s.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Session s WHERE s.userId = :userId AND s.id <> :exceptSessionId")
    void deleteAllByUserIdExcept(@Param("userId") Long userId, @Param("exceptSessionId") Long exceptSessionId);
}
