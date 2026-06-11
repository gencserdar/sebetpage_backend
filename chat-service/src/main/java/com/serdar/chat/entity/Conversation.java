package com.serdar.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"type", "user_a_id", "user_b_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {
    /** DIRECT = 1:1 chat. MESSAGING_GROUP = private group chat (FriendsPanel). */
    public enum Type { DIRECT, MESSAGING_GROUP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private Type type;

    @Column(name = "user_a_id") private Long userAId;
    @Column(name = "user_b_id") private Long userBId;

    private String title;
    @Column(length = 1024) private String description;
    @Column(name = "image_url", length = 1024) private String imageUrl;
    @Column(name = "created_by_id") private Long createdById;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;

    @Column(nullable = false) private LocalDateTime createdAt;
}
