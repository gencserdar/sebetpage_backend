package com.serdar.personal.service;

import com.serdar.personal.model.Conversation;
import com.serdar.personal.model.Message;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.MessageRepository;
import com.serdar.personal.repository.ConversationRepository;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.AesGcmService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import com.serdar.personal.model.dto.MessageDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private final MessageRepository msgRepo;
    private final ConversationRepository convRepo;
    private final UserRepository userRepo;
    private final AesGcmService aes;

    @Transactional
    public Message send(Long conversationId, Long senderId, String plaintext) {
        try {
            log.info("MessageService.send() called - ConvId: {}, SenderId: {}, Content: '{}'",
                    conversationId, senderId, plaintext);

            if (plaintext == null || plaintext.isBlank()) {
                log.error("Empty message content");
                throw new IllegalArgumentException("Empty message");
            }
            if (plaintext.length() > 2000) {
                log.error("Message too long: {} characters", plaintext.length());
                throw new IllegalArgumentException("Message too long");
            }

            log.debug("Fetching conversation with ID: {}", conversationId);
            Conversation conv = convRepo.findById(conversationId)
                    .orElseThrow(() -> new RuntimeException("Conversation not found: " + conversationId));
            log.debug("Found conversation: {}", conv);

            log.debug("Fetching user with ID: {}", senderId);
            User sender = userRepo.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + senderId));
            log.debug("Found sender: {}", sender);

            log.debug("Encrypting message content");
            var enc = aes.encrypt(plaintext, AesGcmService.aad(conversationId, senderId));
            log.debug("Message encrypted successfully");

            Message m = Message.builder()
                    .conversation(conv)
                    .sender(sender)
                    .contentCipherB64(enc.getCipherB64())
                    .contentIvB64(enc.getIvB64())
                    .createdAt(LocalDateTime.now())
                    .build();

            log.debug("Saving message to database: {}", m);
            Message saved = msgRepo.save(m);
            log.info("Message saved successfully with ID: {}", saved.getId());

            return saved;

        } catch (Exception e) {
            log.error("Error in MessageService.send()", e);
            throw e;
        }
    }

    /** Şifre çözülmüş içerikle DTO dönen sayfalama (DESC by createdAt) */
    public Page<MessageDTO> getPage(Long convId, int page, int size) {
        try {
            log.debug("Getting messages page - ConvId: {}, Page: {}, Size: {}", convId, page, size);

            Page<Message> p = msgRepo.findByConversationIdOrderByCreatedAtDesc(
                    convId, PageRequest.of(page, size));

            log.debug("Found {} messages for conversation {}", p.getTotalElements(), convId);

            return p.map(m -> {
                try {
                    String decrypted = aes.decrypt(
                            m.getContentIvB64(),
                            m.getContentCipherB64(),
                            AesGcmService.aad(m.getConversation().getId(), m.getSender().getId())
                    );

                    return new MessageDTO(
                            m.getId(),
                            m.getSender().getId(),
                            m.getConversation().getId(),
                            decrypted,
                            m.getCreatedAt()
                    );
                } catch (Exception e) {
                    log.error("Error decrypting message {}", m.getId(), e);
                    return new MessageDTO(
                            m.getId(),
                            m.getSender().getId(),
                            m.getConversation().getId(),
                            "[Decryption failed]",
                            m.getCreatedAt()
                    );
                }
            });
        } catch (Exception e) {
            log.error("Error getting messages page", e);
            throw e;
        }
    }

    /**
     * İlk açılış için: son 'limit' mesajı getirir, UI’da doğal okuma sırası için ASC döner.
     * Mevcut repository metodunu (DESC) kullanıp listede reverse yapıyoruz.
     */
    public List<MessageDTO> getLatestAscending(Long convId, int limit) {
        try {
            log.debug("Getting latest ascending messages - ConvId: {}, Limit: {}", convId, limit);

            Page<Message> p = msgRepo.findByConversationIdOrderByCreatedAtDesc(
                    convId, PageRequest.of(0, limit));

            List<MessageDTO> descDtos = new ArrayList<>(p.map(m -> {
                try {
                    String decrypted = aes.decrypt(
                            m.getContentIvB64(),
                            m.getContentCipherB64(),
                            AesGcmService.aad(m.getConversation().getId(), m.getSender().getId())
                    );
                    return new MessageDTO(
                            m.getId(),
                            m.getSender().getId(),
                            m.getConversation().getId(),
                            decrypted,
                            m.getCreatedAt()
                    );
                } catch (Exception e) {
                    log.error("Error decrypting message {}", m.getId(), e);
                    return new MessageDTO(
                            m.getId(),
                            m.getSender().getId(),
                            m.getConversation().getId(),
                            "[Decryption failed]",
                            m.getCreatedAt()
                    );
                }
            }).getContent());

            // DESC → ASC
            Collections.reverse(descDtos);
            return descDtos;

        } catch (Exception e) {
            log.error("Error getting latest ascending messages", e);
            throw e;
        }
    }
}
