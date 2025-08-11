package com.serdar.personal.service;

import com.serdar.personal.model.Conversation;
import com.serdar.personal.model.ConversationParticipant;
import com.serdar.personal.repository.ConversationParticipantRepository;
import com.serdar.personal.repository.ConversationRepository;
import com.serdar.personal.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository convRepo;
    private final ConversationParticipantRepository partRepo;
    private final UserRepository userRepo;

    @Transactional
    public Conversation getOrCreateDirect(Long u1, Long u2) {
        Long a = Math.min(u1, u2);
        Long b = Math.max(u1, u2);

        return convRepo.findByTypeAndUserAIdAndUserBId(Conversation.Type.DIRECT, a, b)
                .orElseGet(() -> {
                    Conversation c = Conversation.builder()
                            .type(Conversation.Type.DIRECT)
                            .userAId(a).userBId(b)
                            .build();
                    c = convRepo.save(c);

                    partRepo.save(ConversationParticipant.builder()
                            .conversation(c).user(userRepo.getReferenceById(u1)).build());
                    partRepo.save(ConversationParticipant.builder()
                            .conversation(c).user(userRepo.getReferenceById(u2)).build());
                    return c;
                });
    }
}
