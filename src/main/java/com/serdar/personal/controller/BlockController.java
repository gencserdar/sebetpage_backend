package com.serdar.personal.controller;

import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.service.BlockService;
import com.serdar.personal.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blocks")
public class BlockController {

    private final BlockService blockService;
    private final UserContextService userContextService;
    private final UserRepository userRepository;

    /** Engelle (ID ile) */
    @PostMapping("/{blockedId}")
    public Map<String, Object> block(@PathVariable Long blockedId) {
        User me = userContextService.getCurrentUser();
        blockService.block(me.getId(), blockedId);
        return Map.of("status", "blocked", "blockedId", blockedId);
    }

    /** Engeli kaldır (ID ile) */
    @DeleteMapping("/{blockedId}")
    public Map<String, Object> unblock(@PathVariable Long blockedId) {
        User me = userContextService.getCurrentUser();
        blockService.unblock(me.getId(), blockedId);
        return Map.of("status", "unblocked", "blockedId", blockedId);
    }

    /** Benim block listem (basit liste) */
    @GetMapping
    public List<Map<String, Object>> myBlocks() {
        User me = userContextService.getCurrentUser();
        return blockService.myBlocks(me.getId()).stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("blockedId", b.getBlocked().getId());
                    m.put("blockedNickname", b.getBlocked().getNickname());
                    m.put("profileImageUrl", b.getBlocked().getProfileImageUrl());
                    if (b.getCreatedAt() != null) {
                        m.put("createdAt", b.getCreatedAt().toString());
                    }
                    return m;
                })
                .toList();
    }



    /** (Opsiyonel) bir kullanıcıyla blok durumu - FE kararları için kullanışlı */
    @GetMapping("/status/{nickname}")
    public Map<String, Object> getBlockStatus(@PathVariable String nickname) {
        User me = userContextService.getCurrentUser();
        User other = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean blockedByMe = blockService.isBlockedByMe(me.getId(), other.getId());
        boolean blocksMe    = blockService.blocksMe(me.getId(), other.getId());
        return Map.of(
                "blockedByMe", blockedByMe,
                "blocksMe", blocksMe,
                "either", (blockedByMe || blocksMe)
        );
    }
}
