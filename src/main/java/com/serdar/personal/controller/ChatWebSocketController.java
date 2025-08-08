package com.serdar.personal.controller;

import com.serdar.personal.model.dto.ChatMessage;
import com.serdar.personal.service.ChatWebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
public class ChatWebSocketController {

    @Autowired
    private ChatWebSocketService chatWebSocketService;

    @MessageMapping("/chat")
    public void handleChatMessage(@Payload ChatMessage message, Principal principal) {
        // Verify that the sender matches the authenticated user
        if (principal != null && principal.getName().equals(message.getFrom())) {
            chatWebSocketService.handlePrivateMessage(message);
        } else {
            System.out.println("Unauthorized message attempt from: " +
                    (principal != null ? principal.getName() : "unknown"));
        }
    }
}