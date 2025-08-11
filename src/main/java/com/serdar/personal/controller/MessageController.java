package com.serdar.personal.controller;

import com.serdar.personal.service.MessageService;
import com.serdar.personal.model.dto.MessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/{convId}/messages")
public class MessageController {

    private final MessageService messageService;

    public record SendMessageRequest(Long senderId, String content) {}

    @PostMapping
    public void send(@PathVariable Long convId,
                     @RequestBody SendMessageRequest req) {
        messageService.send(convId, req.senderId(), req.content());
    }

    /** Mevcut: DESC sayfalı liste */
    @GetMapping
    public Page<MessageDTO> list(@PathVariable Long convId,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return messageService.getPage(convId, page, size);
    }

    /** YENİ: Chat açılışında son N mesajı ASC sırayla almak için */
    @GetMapping("/latest")
    public List<MessageDTO> latest(@PathVariable Long convId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return messageService.getLatestAscending(convId, limit);
    }
}
