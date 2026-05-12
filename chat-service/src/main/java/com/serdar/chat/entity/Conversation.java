package com.serdar.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"type", "user_a_id", "user_b_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    public enum Type { DIRECT, GROUP, MESSAGING_GROUP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type type;

    @Column(name = "user_a_id") private Long userAId;
    @Column(name = "user_b_id") private Long userBId;

    private String title;

    @Column(nullable = false) private LocalDateTime createdAt;
}
