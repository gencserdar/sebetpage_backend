package com.serdar.chat.repository;

import com.serdar.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);

    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.conversationId = :cid AND m.senderId <> :meId " +
           "AND (:lastRead IS NULL OR m.createdAt > :lastRead)")
    long countUnreadFor(@Param("cid") Long conversationId,
                        @Param("meId") Long meId,
                        @Param("lastRead") LocalDateTime lastRead);

    @Query("SELECT m FROM Message m " +
           "WHERE m.conversationId = :cid AND m.senderId = :sid " +
           "AND m.createdAt <= :cutoff ORDER BY m.createdAt DESC")
    java.util.List<Message> lastFromSenderBefore(@Param("cid") Long conversationId,
                                                 @Param("sid") Long senderId,
                                                 @Param("cutoff") LocalDateTime cutoff,
                                                 Pageable pageable);
}
