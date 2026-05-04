package com.serdar.group.service;

import com.serdar.common.ServiceException;
import com.serdar.group.client.UserClient;
import com.serdar.group.entity.Group;
import com.serdar.group.entity.GroupInvite;
import com.serdar.group.entity.GroupMember;
import com.serdar.group.repository.GroupInviteRepository;
import com.serdar.group.repository.GroupMemberRepository;
import com.serdar.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupDomainService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupInviteRepository invites;
    private final UserClient users;

    @Transactional
    public Group createGroup(long creatorId, String name, String description) {
        if (name == null || name.isBlank()) throw ServiceException.invalid("Group name required");
        if (!users.exists(creatorId))       throw ServiceException.notFound("Creator not found");

        Group g = groups.save(Group.builder()
                .name(name)
                .description(description)
                .isPrivate(false)
                .createdBy(creatorId)
                .createdAt(LocalDateTime.now())
                .build());

        members.save(GroupMember.builder()
                .groupId(g.getId())
                .userId(creatorId)
                .joinedAt(LocalDateTime.now())
                .role(GroupMember.Role.ADMIN)
                .build());

        return g;
    }

    public boolean isMember(long groupId, long userId) {
        return members.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() != GroupMember.Role.PENDING).orElse(false);
    }

    @Transactional
    public void invite(long groupId, long inviterId, long toUserId) {
        if (!isMember(groupId, inviterId))
            throw ServiceException.forbidden("Only active members can invite");
        if (!users.exists(toUserId))
            throw ServiceException.notFound("Invitee not found");
        if (invites.existsByGroupIdAndToUserIdAndStatus(groupId, toUserId, GroupInvite.Status.PENDING))
            throw ServiceException.conflict("Invite already pending");

        invites.save(GroupInvite.builder()
                .groupId(groupId)
                .fromUserId(inviterId)
                .toUserId(toUserId)
                .status(GroupInvite.Status.PENDING)
                .sentAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void respond(long inviteId, long responderId, boolean accept) {
        GroupInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> ServiceException.notFound("Invite not found"));
        if (!inv.getToUserId().equals(responderId))
            throw ServiceException.forbidden("Not your invite");
        if (inv.getStatus() != GroupInvite.Status.PENDING)
            throw ServiceException.precondition("Already responded");

        if (accept) {
            members.save(GroupMember.builder()
                    .groupId(inv.getGroupId())
                    .userId(inv.getToUserId())
                    .joinedAt(LocalDateTime.now())
                    .role(GroupMember.Role.MEMBER)
                    .build());
        }
        invites.delete(inv);
    }

    public List<GroupWithRole> myGroups(long userId) {
        return members.findByUserIdAndRoleNot(userId, GroupMember.Role.PENDING).stream()
                .map(m -> {
                    Group g = groups.findById(m.getGroupId()).orElse(null);
                    return g == null ? null : new GroupWithRole(g, m.getRole());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<Group> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return groups.findByNameContainingIgnoreCase(keyword);
    }

    public record GroupWithRole(Group group, GroupMember.Role role) {}
}
