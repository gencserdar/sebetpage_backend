package com.serdar.personal.repository;

import com.serdar.personal.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    // Unread count: lastReadAt’tan sonra gelen mesaj sayısı
    @Query("""
      SELECT COUNT(m) FROM Message m
      WHERE m.conversation.id = :convId AND m.createdAt > :lastReadAt
    """)
    long countUnread(@Param("convId") Long convId, @Param("lastReadAt") LocalDateTime lastReadAt);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId AND m.createdAt > :lastReadAt")
    int countByConversationIdAndCreatedAtAfter(@Param("conversationId") Long conversationId, @Param("lastReadAt") LocalDateTime lastReadAt);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId")
    int countByConversationId(@Param("conversationId") Long conversationId);

    Optional<Message> findTopByConversationIdAndSenderIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long conversationId, Long senderId, LocalDateTime createdAt
    );
}
