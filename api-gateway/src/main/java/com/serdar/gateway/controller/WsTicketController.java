package com.serdar.gateway.controller;

import com.serdar.gateway.security.CurrentUser;
import com.serdar.gateway.ws.WsTicketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WsTicketController {

    private final WsTicketService tickets;

    @PostMapping("/api/ws-ticket")
    public ResponseEntity<?> issue(HttpServletRequest request) {
        var me = CurrentUser.require();
        var ticket = tickets.issue(
                me.id(),
                me.email(),
                me.nickname(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
        );
        return ResponseEntity.ok(Map.of(
                "ticket", ticket.value(),
                "expiresInSeconds", ticket.expiresInSeconds()
        ));
    }
}
