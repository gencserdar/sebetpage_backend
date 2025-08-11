package com.serdar.personal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// ConversationParticipant.java
@Entity
@Table(name="conversation_participants",
        uniqueConstraints = { @UniqueConstraint(name="uk_conv_user", columnNames = {"conversation_id","user_id"}) },
        indexes = { @Index(name="ix_user", columnList="user_id"), @Index(name="ix_conv", columnList="conversation_id") }
)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationParticipant {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id")
    private User user;

    private LocalDateTime joinedAt = LocalDateTime.now();
    private LocalDateTime lastReadAt;   // unread hesapları için altın anahtar
    private Boolean muted = false;
    private LocalDateTime deletedAt;    // kullanıcı bazlı “gizle/sil”
    private Boolean pinned = false;
    private String role;                // group: admin/member
}

