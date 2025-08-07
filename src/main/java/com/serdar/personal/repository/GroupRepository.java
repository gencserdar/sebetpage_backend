package com.serdar.personal.repository;

import com.serdar.personal.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByNameIgnoreCase(String name);
    List<Group> findByNameContainingIgnoreCase(String keyword);
}

