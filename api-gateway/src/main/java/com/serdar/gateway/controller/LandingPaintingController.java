package com.serdar.gateway.controller;

import com.serdar.gateway.client.UserClient;
import com.serdar.proto.user.LandingPainting;
import com.serdar.proto.user.LandingPaintingList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/landing/paintings")
@RequiredArgsConstructor
public class LandingPaintingController {

    private static final Pattern VISITOR_ID = Pattern.compile("^[0-9a-fA-F\\-]{36}$");

    private final UserClient users;

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "20") int limit) {
        LandingPaintingList list = users.listLandingPaintings(limit);
        return list.getPaintingsList().stream().map(this::toJson).toList();
    }

    @GetMapping("/mine")
    public ResponseEntity<Map<String, Object>> mine(@RequestHeader("X-Visitor-Id") String visitorId) {
        if (!isValidVisitorId(visitorId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid visitor id"));
        }
        LandingPainting painting = users.getVisitorLandingPainting(visitorId);
        if (painting.getId() == 0) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toJson(painting));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestHeader("X-Visitor-Id") String visitorId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (!isValidVisitorId(visitorId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid visitor id"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        LandingPainting saved = users.upsertLandingPainting(
                visitorId,
                file.getBytes(),
                file.getContentType(),
                file.getOriginalFilename());
        return ResponseEntity.ok(toJson(saved));
    }

    private static boolean isValidVisitorId(String visitorId) {
        return visitorId != null && VISITOR_ID.matcher(visitorId).matches();
    }

    private Map<String, Object> toJson(LandingPainting p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("visitorId", p.getVisitorId());
        m.put("imageUrl", p.getImageUrl());
        m.put("createdAt", p.getCreatedAtMillis());
        return m;
    }
}
