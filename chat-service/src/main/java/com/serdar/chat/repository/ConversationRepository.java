package com.serdar.chat.repository;

import com.serdar.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByTypeAndUserAIdAndUserBId(Conversation.Type type, Long userAId, Long userBId);
    Optional<Conversation> findByIdAndDeletedAtIsNull(Long id);
}
