package com.serdar.personal.repository;

import com.serdar.personal.model.Group;
import com.serdar.personal.model.GroupMember;
import com.serdar.personal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByUser(User user);
    List<GroupMember> findByGroup(Group group);
    Optional<GroupMember> findByUserAndGroup(User user, Group group);
}
