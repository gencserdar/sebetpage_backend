package com.serdar.personal.controller;

import com.serdar.personal.model.dto.MessageDTO;
import com.serdar.personal.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/send")
    public void sendMessage(Map<String, Object> payload) {
        try {
            log.info("Received WebSocket message: {}", payload);

            // Extract data from payload
            Long conversationId = Long.valueOf(payload.get("conversationId").toString());
            Long senderId = Long.valueOf(payload.get("senderId").toString());
            String content = payload.get("content").toString();

            log.info("Processing message - ConvId: {}, SenderId: {}, Content: '{}'",
                    conversationId, senderId, content);

            // Save message to database
            var savedMessage = messageService.send(conversationId, senderId, content);
            log.info("Message saved successfully with ID: {}", savedMessage.getId());

            // Create DTO for WebSocket broadcast
            var messageDTO = MessageDTO.builder()
                    .id(savedMessage.getId())
                    .senderId(savedMessage.getSender().getId())
                    .conversationId(savedMessage.getConversation().getId())
                    .content(content) // Use original plaintext for broadcast
                    .createdAt(savedMessage.getCreatedAt())
                    .build();

            // Broadcast to all subscribers of this conversation
            String destination = "/topic/chat/" + conversationId;
            log.info("Broadcasting message to: {}", destination);
            messagingTemplate.convertAndSend(destination, messageDTO);

            log.info("Message processed and broadcasted successfully");

        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", payload, e);
            // You might want to send an error message back to the client
        }
    }
}