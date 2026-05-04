package com.serdar.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_conversation_created",
               columnList = "conversation_id, created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "sender_id", nullable = false)       private Long senderId;

    @Column(name = "content_cipher_b64", nullable = false, length = 4096)
    private String contentCipherB64;

    @Column(name = "content_iv_b64", nullable = false, length = 64)
    private String contentIvB64;

    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
