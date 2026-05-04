package com.serdar.gateway.controller;

import com.serdar.gateway.client.ChatClient;
import com.serdar.gateway.security.CurrentUser;
import com.serdar.proto.chat.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient chat;

    @PostMapping("/conversations/direct")
    public ResponseEntity<?> openDirect(@RequestParam long otherUserId) {
        long me = CurrentUser.require().id();
        Conversation c = chat.getOrCreateDirect(me, otherUserId);
        return ResponseEntity.ok(toRow(c));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> page(@PathVariable long id,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "30") int size) {
        long me = CurrentUser.require().id();
        MessagePage p = chat.getPage(id, me, page, size);
        return ResponseEntity.ok(Map.of(
                "messages", p.getMessagesList().stream().map(ChatController::toMessage).toList(),
                "page", p.getPage(),
                "size", p.getSize(),
                "total", p.getTotal()
        ));
    }

    @GetMapping("/conversations/{id}/latest")
    public ResponseEntity<?> latest(@PathVariable long id, @RequestParam(defaultValue = "30") int limit) {
        long me = CurrentUser.require().id();
        MessageList list = chat.getLatest(id, me, limit);
        return ResponseEntity.ok(list.getMessagesList().stream().map(ChatController::toMessage).toList());
    }

    @PostMapping("/conversations/{id}/send")
    public ResponseEntity<?> send(@PathVariable long id, @RequestBody Map<String, String> body) {
        long me = CurrentUser.require().id();
        ChatMessage m = chat.send(id, me, body.getOrDefault("content", ""));
        return ResponseEntity.ok(toMessage(m));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable long id) {
        long me = CurrentUser.require().id();
        MarkReadResponse r = chat.markRead(id, me);
        return ResponseEntity.ok(Map.of(
                "conversationId", r.getConversationId(),
                "unreadCount", r.getUnreadCount(),
                "totalUnreadCount", r.getTotalUnreadCount(),
                "lastReadAtMillis", r.getLastReadAtMillis()
        ));
    }

    @GetMapping("/conversations/{id}/read-state")
    public ResponseEntity<?> readState(@PathVariable long id) {
        long me = CurrentUser.require().id();
        ReadStateResponse r = chat.readState(id, me);
        return ResponseEntity.ok(Map.of(
                "myLastReadAtMillis", r.getMyLastReadAtMillis(),
                "friendLastReadAtMillis", r.getFriendLastReadAtMillis(),
                "seenMyMessageId", r.getSeenMyMessageId(),
                "friendUserId", r.getFriendUserId(),
                "myUserId", r.getMyUserId()
        ));
    }

    @GetMapping("/unread-counts")
    public ResponseEntity<?> unreadCounts() {
        long me = CurrentUser.require().id();
        UnreadCountsResponse r = chat.unreadCounts(me);
        return ResponseEntity.ok(Map.of(
                "totalCount", r.getTotalCount(),
                "perConversation", r.getPerConversationMap()
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> mine() {
        long me = CurrentUser.require().id();
        List<Map<String, Object>> rows = chat.myConversations(me).getConversationsList()
                .stream().map(ChatController::toRow).toList();
        return ResponseEntity.ok(rows);
    }

    // --- helpers -----------------------------------------------------------

    private static Map<String, Object> toRow(Conversation c) {
        return Map.of(
                "id", c.getId(),
                "type", c.getType().name(),
                "userAId", c.getUserAId(),
                "userBId", c.getUserBId(),
                "title", c.getTitle(),
                "createdAtMillis", c.getCreatedAtMillis()
        );
    }

    private static Map<String, Object> toMessage(ChatMessage m) {
        // The frontend's WsMessageDTO expects `createdAt` as an ISO string —
        // same shape the WS bridge pushes — so REST history and live frames
        // line up and the chat doesn't render `Invalid Date` after a reopen.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("conversationId", m.getConversationId());
        row.put("senderId", m.getSenderId());
        row.put("content", m.getContent());
        row.put("createdAt", Instant.ofEpochMilli(m.getCreatedAtMillis()).toString());
        return row;
    }
}
