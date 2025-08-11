package com.serdar.personal.controller;

import com.serdar.personal.model.dto.MessageDTO;
import com.serdar.personal.model.User;
import com.serdar.personal.service.ChatService;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/conversations/{convId}/messages")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // WS: mesaj gönder
    @MessageMapping("/chat/send")
    public void sendMessage(@Payload Map<String, Object> payload) {
        chatService.processWsSendPayload(payload);
    }

    // WS: presence snapshot tetikleme (deterministik)
    @MessageMapping("/friends/snapshot")
    public void snapshot(Principal principal) {
        if (principal == null) return;
        userRepository.findByEmail(principal.getName())
                .ifPresent(chatService::sendPresenceSnapshotTo);
    }

    // REST: history
    @GetMapping
    public Page<MessageDTO> list(@PathVariable Long convId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return chatService.getPage(convId, page, size);
    }

    @GetMapping("/latest")
    public List<MessageDTO> latest(@PathVariable Long convId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return chatService.getLatestAscending(convId, limit);
    }

    // READ/SEEN
    @PostMapping("/read")
    public Map<String, Object> markRead(@PathVariable Long convId, Principal principal) {
        return chatService.markReadAndBroadcast(convId, principal);
    }

    @GetMapping("/read-state")
    public Map<String, Object> readState(@PathVariable Long convId, Principal principal) {
        return chatService.getReadState(convId, principal);
    }
}
