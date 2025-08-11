package com.serdar.personal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "conversations",
        uniqueConstraints = {
                @UniqueConstraint(name="uk_direct_pair", columnNames = {"type","user_a_id","user_b_id"})
        },
        indexes = { @Index(name="ix_conv_type", columnList="type") }
)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    public enum Type { DIRECT, GROUP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @Column(nullable=false)
    private Type type;

    // DIRECT için ikiliyi tekilleştirmek adına (min,max) sırala
    @Column(name="user_a_id") private Long userAId;  // nullable: GROUP'ta null
    @Column(name="user_b_id") private Long userBId;

    private String title; // GROUP ismi vs

    @Column(nullable=false, updatable=false)
    private LocalDateTime createdAt;

    // Mapping sadece ilişkiyi göstermek için
    @OneToMany(mappedBy="conversation", fetch=FetchType.LAZY)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}