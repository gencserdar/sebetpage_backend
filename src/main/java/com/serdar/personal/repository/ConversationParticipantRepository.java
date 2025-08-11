package com.serdar.personal.repository;

import com.serdar.personal.model.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {
    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.user.id = :userId AND cp.deletedAt IS NULL")
    Optional<ConversationParticipant> findByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.user.id = :userId AND cp.deletedAt IS NULL")
    List<ConversationParticipant> findByUserIdAndDeletedAtIsNull(@Param("userId") Long userId);

    @Query("SELECT cp FROM ConversationParticipant cp WHERE cp.conversation.id = :conversationId AND cp.deletedAt IS NULL")
    List<ConversationParticipant> findByConversationIdAndDeletedAtIsNull(@Param("conversationId") Long conversationId);
}
