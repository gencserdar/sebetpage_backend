package com.serdar.group.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
// `groups` is a reserved word in MySQL 8, so we use `user_groups` to avoid the
// "you have an error in your SQL syntax" that Hibernate otherwise emits on DDL.
@Table(name = "user_groups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Group {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String name;
    @Column(length = 1000)    private String description;
    @Column(nullable = false) private Boolean isPrivate = false;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;
}
