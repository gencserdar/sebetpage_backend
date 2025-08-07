package com.serdar.personal.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "group_members")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Group group;

    private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, MEMBER, PENDING

    public enum Role {
        ADMIN, MEMBER, PENDING
    }

    // Getters/Setters
}
