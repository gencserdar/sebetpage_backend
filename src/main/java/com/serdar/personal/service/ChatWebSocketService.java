package com.serdar.personal.service;

import com.serdar.personal.model.dto.ChatMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void handlePrivateMessage(ChatMessage message) {
        message.setTimestamp(LocalDateTime.now().toString());

        System.out.println("=== Sending SockJS Private Message ===");
        System.out.println("From: " + message.getFrom());
        System.out.println("To: " + message.getTo());
        System.out.println("Content: " + message.getContent());

        try {
            // Send to recipient
            messagingTemplate.convertAndSendToUser(
                    message.getTo(),
                    "/queue/messages",
                    message
            );

            System.out.println("Message sent to recipient: " + message.getTo());

            // Send confirmation back to sender
            messagingTemplate.convertAndSendToUser(
                    message.getFrom(),
                    "/queue/messages",
                    message
            );

            System.out.println("Confirmation sent to sender: " + message.getFrom());

        } catch (Exception e) {
            System.err.println("Failed to send SockJS message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void broadcastMessage(ChatMessage message) {
        System.out.println("Broadcasting message to /topic/messages");
        messagingTemplate.convertAndSend("/topic/messages", message);
    }
}