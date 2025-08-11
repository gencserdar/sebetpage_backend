package com.serdar.personal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name="messages",
        indexes = @Index(name="ix_msg_conv_time", columnList="conversation_id,created_at"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="sender_id")
    private User sender;

    // Şifreli içerik
    @Column(name="content_cipher_b64", nullable=false, length=4096)
    private String contentCipherB64;

    @Column(name="content_iv_b64", nullable=false, length=24)
    private String contentIvB64;

    @Column(nullable=false)
    private LocalDateTime createdAt = LocalDateTime.now();
}


