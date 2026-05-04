package com.serdar.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_participants", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"conversation_id", "user_id"})
}, indexes = {
        @Index(columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "user_id", nullable = false)         private Long userId;

    @Column(nullable = false) private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
    private LocalDateTime deletedAt;
    private Boolean muted = false;
    private Boolean pinned = false;
    private String role;
}
