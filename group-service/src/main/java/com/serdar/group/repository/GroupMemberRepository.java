package com.serdar.group.repository;

import com.serdar.group.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    List<GroupMember> findByUserIdAndRoleNot(Long userId, GroupMember.Role excluded);
}
