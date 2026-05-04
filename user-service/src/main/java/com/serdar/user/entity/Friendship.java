package com.serdar.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Friendships are stored canonical (user1Id &lt; user2Id) so we only ever have one
 * row per pair. FKs are stored as plain IDs — we no longer share JPA relations
 * across services.
 */
@Entity
@Table(name = "friendships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
}, indexes = {
        @Index(columnList = "user1_id"),
        @Index(columnList = "user2_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false) private Long user1Id;
    @Column(name = "user2_id", nullable = false) private Long user2Id;

    @Column(nullable = false) private LocalDateTime createdAt;
}
