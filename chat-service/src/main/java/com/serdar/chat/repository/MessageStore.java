package com.serdar.chat.repository;

import com.serdar.chat.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageStore {
    Message save(Message message);

    void deleteByConversationId(long conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(long conversationId, Pageable pageable);

    long countByConversationId(long conversationId);

    long countUnreadFor(long conversationId, long meId, LocalDateTime lastRead);

    List<Message> findUnreadMessages(long conversationId, long meId, LocalDateTime lastRead);

    List<Message> lastFromSenderBefore(long conversationId, long senderId, LocalDateTime cutoff, Pageable pageable);
}
