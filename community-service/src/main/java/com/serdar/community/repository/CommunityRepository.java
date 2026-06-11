package com.serdar.community.repository;

import com.serdar.community.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Long> {
    List<Community> findByNameContainingIgnoreCase(String keyword);
}
