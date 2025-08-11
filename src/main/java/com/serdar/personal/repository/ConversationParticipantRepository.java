package com.serdar.personal.repository;

import com.serdar.personal.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    Optional<ConversationParticipant> findByConversationIdAndUserId(Long convId, Long userId);
    List<ConversationParticipant> findByConversationId(Long convId);
}
