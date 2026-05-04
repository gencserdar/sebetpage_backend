package com.serdar.chat.service;

import com.serdar.chat.entity.Conversation;
import com.serdar.chat.entity.ConversationParticipant;
import com.serdar.chat.repository.ConversationParticipantRepository;
import com.serdar.chat.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;

    /** Canonicalise direct conversations so (a,b) and (b,a) hit the same row. */
    @Transactional
    public Conversation getOrCreateDirect(long u1, long u2) {
        long a = Math.min(u1, u2), b = Math.max(u1, u2);
        return conversations.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b)
                .orElseGet(() -> {
                    Conversation c = conversations.save(Conversation.builder()
                            .type(Conversation.Type.DIRECT)
                            .userAId(a).userBId(b)
                            .createdAt(LocalDateTime.now())
                            .build());
                    participants.save(ConversationParticipant.builder()
                            .conversationId(c.getId()).userId(a).joinedAt(LocalDateTime.now()).build());
                    participants.save(ConversationParticipant.builder()
                            .conversationId(c.getId()).userId(b).joinedAt(LocalDateTime.now()).build());
                    return c;
                });
    }
}
