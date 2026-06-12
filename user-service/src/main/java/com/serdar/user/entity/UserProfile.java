package com.serdar.user.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Profile slice of the user. The id matches the credential id in auth-service
 * and is assigned by auth-service on registration — we do not generate it here.
 * email/nickname are mirrored from auth-service so we can run local searches
 * without a cross-service call on every query.
 */
@Entity
@Table(name = "user_profiles", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "nickname")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    private Long id;  // assigned, not generated

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String nickname;

    private String name;
    private String surname;

    @Column(length = 512)
    private String profileImageUrl;

    @Column(length = 180, nullable = false)
    @Builder.Default
    private String bio = "";

    @Column(name = "social_links_json", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String socialLinksJson = "[]";

    @Column(name = "profile_card_json", columnDefinition = "TEXT", nullable = false)
    @Builder.Default
    private String profileCardJson = "{\"widgets\":[]}";
}
