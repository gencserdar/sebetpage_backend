package com.serdar.chat.service;

import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.ConversationParticipant;
import com.serdar.chat.repository.ConversationParticipantRepository;
import com.serdar.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;

    /**
     * Canonicalise direct conversations so (a,b) and (b,a) hit the same row.
     *
     * Race-safety: the conversations table has a unique constraint on
     * (type, user_a_id, user_b_id). Two concurrent calls for the same pair
     * can both pass the initial findBy… check and then race to insert.
     * The loser gets a DataIntegrityViolationException; we catch it and
     * fall back to a second findBy… which is now guaranteed to succeed.
     */
    @Transactional
    public Conversation getOrCreateDirect(long u1, long u2) {
        long a = Math.min(u1, u2), b = Math.max(u1, u2);

        var existing = conversations.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b);
        if (existing.isPresent()) return existing.get();

        try {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            Conversation c = conversations.save(Conversation.builder()
                    .type(Conversation.Type.DIRECT)
                    .userAId(a).userBId(b)
                    .createdAt(now)
                    .build());
            participants.save(ConversationParticipant.builder()
                    .conversationId(c.getId()).userId(a).joinedAt(now).build());
            participants.save(ConversationParticipant.builder()
                    .conversationId(c.getId()).userId(b).joinedAt(now).build());
            return c;
        } catch (DataIntegrityViolationException ex) {
            // Another thread won the race and inserted first. The unique
            // constraint did its job — just return the row they created.
            return conversations.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unique constraint fired but row not found for direct conversation (" + a + "," + b + ")", ex));
        }
    }
}
