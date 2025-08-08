package com.serdar.personal.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessage {
    private String from;
    private String to;
    private String content;
    private String timestamp; // veya LocalDateTime
}
