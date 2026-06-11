package com.serdar.community.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"community_id", "user_id"})
}, indexes = {
        @Index(columnList = "community_id"),
        @Index(columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommunityMember {

    public enum Role { MEMBER, ADMIN, PENDING }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false) private Long communityId;
    @Column(name = "user_id", nullable = false)  private Long userId;

    @Column(nullable = false) private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private Role role;
}
