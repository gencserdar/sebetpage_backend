package com.serdar.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_blocks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"})
}, indexes = {
        @Index(columnList = "blocker_id"),
        @Index(columnList = "blocked_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false) private Long blockerId;
    @Column(name = "blocked_id", nullable = false) private Long blockedId;
    @Column(nullable = false) private LocalDateTime createdAt;
}
