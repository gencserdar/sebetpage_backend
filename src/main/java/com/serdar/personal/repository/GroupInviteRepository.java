package com.serdar.personal.repository;

import com.serdar.personal.model.GroupInvite;
import com.serdar.personal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupInviteRepository extends JpaRepository<GroupInvite, Long> {
    List<GroupInvite> findByToUserAndStatus(User toUser, GroupInvite.InviteStatus status);
}
