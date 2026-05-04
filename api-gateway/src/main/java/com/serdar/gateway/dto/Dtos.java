package com.serdar.gateway.dto;

import lombok.*;

/**
 * HTTP-facing DTOs. We keep them as simple POJOs so JSON serialisation works
 * out of the box — the proto types are an internal wire format only.
 */
public class Dtos {
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AuthRequest {
        private String email;
        private String password;
        private boolean rememberMe;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AuthResponse {
        private String token;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {
        private String name;
        private String surname;
        private String email;
        private String nickname;
        private String password;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ResetPasswordRequest {
        private String newPassword;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserDTO {
        private Long id;
        private String email;
        private String name;
        private String surname;
        private Boolean activated;
        private String role;
        private String nickname;
        private String profileImageUrl;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FieldUpdateResponse {
        // Field names line up with the frontend's profileService.FieldUpdateResponse
        // shape ({field, value}). Renaming these to fieldName/newValue silently
        // serializes as `{fieldName, newValue}` which the UI reads as undefined,
        // showing "Not set" and routing the user to /profile/undefined.
        private String field;
        private String value;
    }
}
