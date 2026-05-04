package com.serdar.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "friend_requests", indexes = {
        @Index(columnList = "from_user_id"),
        @Index(columnList = "to_user_id"),
        @Index(columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendRequest {

    public enum Status { PENDING, ACCEPTED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id", nullable = false) private Long fromUserId;
    @Column(name = "to_user_id", nullable = false)   private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private Status status;

    @Column(nullable = false) private LocalDateTime sentAt;
}
