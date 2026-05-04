package com.serdar.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Auth-service's slice of the original "users" table — only the fields needed
 * to authenticate and identify a user. Display fields (name, surname, photo)
 * live in user-service's profiles table, keyed by the same id.
 */
@Entity
@Table(name = "credentials", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "nickname")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activated = false;

    @Column(length = 64)
    private String activationCode;

    @Column(length = 64)
    private String resetCode;

    @Column(name = "reset_code_hash", length = 128)
    private String resetCodeHash;

    @Column(name = "reset_code_expires_at")
    private LocalDateTime resetCodeExpiresAt;

    @Column(name = "reset_code_attempts")
    private Integer resetCodeAttempts;

    @Column(name = "refresh_token", length = 1024)
    private String refreshToken;

    /**
     * Sticky flag captured at login time; used on refresh to decide which TTL
     * to apply to the newly-rotated refresh token. Sliding-window style:
     * each successful refresh resets the session to its original window.
     *
     * columnDefinition pins a DB-side default so ddl-auto=update can ADD this
     * column against an existing populated `credentials` table without blowing up.
     */
    @Column(name = "remember_me", columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean rememberMe = false;

    /*
     * Pending email-change. The new email and a 6-digit code sit here until
     * the user types the code back; bad attempts increment the counter, and
     * we invalidate everything once the threshold is crossed. All four are
     * cleared on success/cancel so a user can't have two pending changes.
     */
    @Column(name = "pending_email_code", length = 16)
    private String pendingEmailCode;

    @Column(name = "pending_email_new", length = 254)
    private String pendingEmailNew;

    @Column(name = "pending_email_expires_at")
    private LocalDateTime pendingEmailExpiresAt;

    @Column(name = "pending_email_attempts")
    private Integer pendingEmailAttempts;

    /*
     * Pending password-change. We store the *already-hashed* new password
     * so a leaked DB row never exposes the cleartext, and the row swap on
     * confirm is just a copy.
     */
    @Column(name = "pending_password_code", length = 16)
    private String pendingPasswordCode;

    @Column(name = "pending_password_new_hash", length = 100)
    private String pendingPasswordNewHash;

    @Column(name = "pending_password_expires_at")
    private LocalDateTime pendingPasswordExpiresAt;

    @Column(name = "pending_password_attempts")
    private Integer pendingPasswordAttempts;
}
