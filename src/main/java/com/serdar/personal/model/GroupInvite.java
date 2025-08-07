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
@Table(name = "group_invites")
public class GroupInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User fromUser;

    @ManyToOne
    private User toUser;

    @ManyToOne
    private Group group;

    @Enumerated(EnumType.STRING)
    private InviteStatus status; // PENDING, ACCEPTED, REJECTED

    private LocalDateTime sentAt;

    public enum InviteStatus {
        PENDING, ACCEPTED, REJECTED
    }

    // Getters/Setters
}
