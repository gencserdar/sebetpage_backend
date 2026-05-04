package com.serdar.chat.repository;

import com.serdar.chat.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);
    Optional<ConversationParticipant> findByConversationIdAndUserIdAndDeletedAtIsNull(Long conversationId, Long userId);
    List<ConversationParticipant> findByConversationIdAndDeletedAtIsNull(Long conversationId);
    List<ConversationParticipant> findByUserIdAndDeletedAtIsNull(Long userId);
}
