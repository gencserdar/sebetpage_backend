package com.serdar.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String contentCipherB64;
    private String contentIvB64;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;
    @Builder.Default
    private boolean deleted = false;
}
