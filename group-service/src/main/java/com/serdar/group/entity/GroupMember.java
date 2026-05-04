package com.serdar.group.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"group_id", "user_id"})
}, indexes = {
        @Index(columnList = "group_id"),
        @Index(columnList = "user_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupMember {

    public enum Role { MEMBER, ADMIN, PENDING }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false) private Long groupId;
    @Column(name = "user_id", nullable = false)  private Long userId;

    @Column(nullable = false) private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private Role role;
}
