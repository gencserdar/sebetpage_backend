package com.serdar.personal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@lombok.Value
@Data
@AllArgsConstructor
@Builder
public class MessageDTO {
    Long id;
    Long senderId;
    Long conversationId;
    String content;              // DECRYPTED
    java.time.LocalDateTime createdAt;
}
