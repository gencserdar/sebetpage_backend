package com.serdar.group.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_invites", indexes = {
        @Index(columnList = "group_id"),
        @Index(columnList = "to_user_id"),
        @Index(columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupInvite {

    public enum Status { PENDING, ACCEPTED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)      private Long groupId;
    @Column(name = "from_user_id", nullable = false)  private Long fromUserId;
    @Column(name = "to_user_id", nullable = false)    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private Status status;

    @Column(nullable = false) private LocalDateTime sentAt;
}
