package com.serdar.personal.repository;

import com.serdar.personal.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    // Unread count: lastReadAt’tan sonra gelen mesaj sayısı
    @Query("""
      SELECT COUNT(m) FROM Message m
      WHERE m.conversation.id = :convId AND m.createdAt > :lastReadAt
    """)
    long countUnread(@Param("convId") Long convId, @Param("lastReadAt") LocalDateTime lastReadAt);
}
