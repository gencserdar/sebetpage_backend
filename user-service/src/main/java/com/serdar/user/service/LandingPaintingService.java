package com.serdar.user.service;

import com.serdar.user.entity.LandingPainting;
import com.serdar.user.repository.LandingPaintingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LandingPaintingService {

    private final LandingPaintingRepository repo;
    private final LocalImageStorageService imageStorage;

    public List<LandingPainting> listRecent(int limit) {
        int capped = Math.max(1, Math.min(limit, 50));
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, capped));
    }

    public Optional<LandingPainting> findByVisitorId(String visitorId) {
        return repo.findByVisitorId(visitorId);
    }

    @Transactional
    public LandingPainting upsert(String visitorId, byte[] bytes, String contentType, String filename) {
        ImageValidator.Validated validated = ImageValidator.validateLandingPainting(bytes, visitorId);
        String url = imageStorage.upload(
                validated.bytes(), validated.canonicalContentType(), validated.safeFilename());

        Optional<LandingPainting> existing = repo.findByVisitorId(visitorId);
        if (existing.isPresent()) {
            LandingPainting painting = existing.get();
            String previousUrl = painting.getImageUrl();
            painting.setImageUrl(url);
            painting.setCreatedAt(Instant.now());
            LandingPainting saved = repo.save(painting);
            if (previousUrl != null && !previousUrl.isBlank() && !previousUrl.equals(url)) {
                imageStorage.deleteByPublicUrl(previousUrl);
            }
            return saved;
        }

        return repo.save(LandingPainting.builder()
                .visitorId(visitorId)
                .imageUrl(url)
                .createdAt(Instant.now())
                .build());
    }
}
