package com.serdar.personal.repository;

import com.serdar.personal.model.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByTypeAndUserAIdAndUserBId(Conversation.Type type, Long a, Long b);

    // Kullanıcının dahil olduğu sohbetler (participant üzerinden)
    @Query("""
      SELECT cp.conversation FROM ConversationParticipant cp
      WHERE cp.user.id = :userId AND cp.deletedAt IS NULL
      ORDER BY cp.conversation.createdAt DESC
    """)
    Page<Conversation> findUserConversations(@Param("userId") Long userId, Pageable pageable);
}