package com.serdar.community.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "communities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Community {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String name;
    @Column(length = 1000)    private String description;
    @Column(nullable = false) private Boolean isPrivate = false;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;
}
