package com.serdar.user.repository;

import com.serdar.user.entity.LandingPainting;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LandingPaintingRepository extends JpaRepository<LandingPainting, Long> {

    Optional<LandingPainting> findByVisitorId(String visitorId);

    List<LandingPainting> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
