package com.serdar.group.repository;

import com.serdar.group.entity.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {
    boolean existsByGroupIdAndToUserIdAndStatus(Long groupId, Long toUserId, GroupInvite.Status status);
}
