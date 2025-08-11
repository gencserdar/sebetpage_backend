package com.serdar.personal.controller;

import com.serdar.personal.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatManagerController {

    private final ChatService chatService;

    @GetMapping("/unread-counts")
    public Map<String, Object> unreadCounts(Principal principal) {
        return chatService.getUnreadCounts(principal);
    }

    @PostMapping("/conversations/{conversationId}/mark-read")
    public Map<String, Object> markReadByManager(@PathVariable Long conversationId, Principal principal) {
        return chatService.markReadByManager(conversationId, principal);
    }
}
