package com.serdar.group.repository;

import com.serdar.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    List<Group> findByNameContainingIgnoreCase(String keyword);
}
