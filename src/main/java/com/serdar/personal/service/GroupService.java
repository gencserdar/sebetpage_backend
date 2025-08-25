package com.serdar.personal.service;

import com.serdar.personal.model.Group;
import com.serdar.personal.model.GroupMember;
import com.serdar.personal.repository.GroupMemberRepository;
import com.serdar.personal.repository.GroupRepository;
import com.serdar.personal.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepo;
    private final GroupMemberRepository memberRepo;
    private final UserRepository userRepo;

    @Transactional
    public Group createGroup(String name, String description, String creatorEmail) {
        var creator = userRepo.findByEmail(creatorEmail).orElseThrow();
        var g = Group.builder()
                .name(name).description(description)
                .isPrivate(true)
                .createdBy(creator).createdAt(LocalDateTime.now())
                .build();
        g = groupRepo.save(g);

        memberRepo.save(GroupMember.builder()
                .group(g).user(creator)
                .role(GroupMember.Role.ADMIN)
                .joinedAt(LocalDateTime.now())
                .build());
        return g;
    }

    public boolean isMember(Long groupId, Long userId) {
        return memberRepo.existsByGroupIdAndUserId(groupId, userId);
    }

    public GroupMember requireActiveMember(Long groupId, String email) throws AccessDeniedException {
        var user = userRepo.findByEmail(email).orElseThrow();
        var gm = memberRepo.findByGroupIdAndUserId(groupId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Not a member"));
        if (gm.getRole() == GroupMember.Role.PENDING)
            throw new AccessDeniedException("Pending member");
        return gm;
    }

    public List<Map<String,Object>> myGroups(String email) {
        var user = userRepo.findByEmail(email).orElseThrow();
        return memberRepo.findByUserIdAndRoleNot(user.getId(), GroupMember.Role.PENDING)
                .stream()
                .map(m -> {
                    Map<String,Object> dto = new HashMap<>();
                    dto.put("groupId", m.getGroup().getId());
                    dto.put("name", m.getGroup().getName());
                    dto.put("role", m.getRole().name());
                    return dto;
                })
                .toList();
    }
}

