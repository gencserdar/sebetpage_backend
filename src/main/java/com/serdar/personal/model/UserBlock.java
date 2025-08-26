package com.serdar.personal.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Table(
        name = "user_blocks",
        uniqueConstraints = { @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}) },
        indexes = {
                @Index(name = "idx_user_blocks_blocker", columnList = "blocker_id"),
                @Index(name = "idx_user_blocks_blocked", columnList = "blocked_id")
        }
)
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Engelleyen
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    // Engellenen
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
