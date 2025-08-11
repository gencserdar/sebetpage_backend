package com.serdar.personal.controller;

import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.service.AuthService;
import com.serdar.personal.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations/direct")
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private final AuthService authService; // principal -> current user

    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(@RequestParam String friendEmail, Principal principal) {
        try {
            log.info("Resolving conversation - Principal: {}, FriendEmail: {}",
                    principal != null ? principal.getName() : "null", friendEmail);

            if (principal == null) {
                log.warn("Principal is null - unauthorized access");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            String currentUserEmail = principal.getName();
            log.debug("Looking for current user with email: {}", currentUserEmail);

            var meOpt = userRepository.findByEmail(currentUserEmail);
            if (meOpt.isEmpty()) {
                log.warn("Current user not found in database: {}", currentUserEmail);
                return ResponseEntity.status(401).body(Map.of("error", "Current user not found"));
            }

            log.debug("Looking for friend user with email: {}", friendEmail);
            var friendOpt = userRepository.findByEmail(friendEmail);
            if (friendOpt.isEmpty()) {
                log.warn("Friend user not found in database: {}", friendEmail);
                return ResponseEntity.status(404).body(Map.of("error", "Friend not found"));
            }

            var me = meOpt.get();
            var friend = friendOpt.get();

            log.debug("Creating/getting conversation between users {} and {}", me.getId(), friend.getId());
            var conv = conversationService.getOrCreateDirect(me.getId(), friend.getId());

            var response = Map.of(
                    "conversationId", conv.getId(),
                    "myUserId", me.getId(),
                    "friendUserId", friend.getId()
            );

            log.info("Successfully resolved conversation: {}", response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error resolving conversation for principal: {}, friendEmail: {}",
                    principal != null ? principal.getName() : "null", friendEmail, e);
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
}