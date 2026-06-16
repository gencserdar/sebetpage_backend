package com.serdar.community.service;

import com.serdar.common.ServiceException;
import com.serdar.community.client.UserClient;
import com.serdar.community.entity.Community;
import com.serdar.community.entity.CommunityInvite;
import com.serdar.community.entity.CommunityMember;
import com.serdar.community.search.CommunitySearchIndexService;
import com.serdar.community.repository.CommunityInviteRepository;
import com.serdar.community.repository.CommunityMemberRepository;
import com.serdar.community.repository.CommunityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityDomainService {

    private final CommunityRepository communities;
    private final CommunityMemberRepository members;
    private final CommunityInviteRepository invites;
    private final UserClient users;
    private final CommunitySearchIndexService searchIndex;

    @Transactional
    public Community createCommunity(long creatorId, String name, String description) {
        if (name == null || name.isBlank()) throw ServiceException.invalid("Community name required");
        if (!users.exists(creatorId))       throw ServiceException.notFound("Creator not found");

        Community c = communities.save(Community.builder()
                .name(name)
                .description(description)
                .isPrivate(false)
                .createdBy(creatorId)
                .createdAt(LocalDateTime.now())
                .build());

        members.save(CommunityMember.builder()
                .communityId(c.getId())
                .userId(creatorId)
                .joinedAt(LocalDateTime.now())
                .role(CommunityMember.Role.ADMIN)
                .build());

        searchIndex.index(c);
        return c;
    }

    public boolean isMember(long communityId, long userId) {
        return members.findByCommunityIdAndUserId(communityId, userId)
                .map(m -> m.getRole() != CommunityMember.Role.PENDING).orElse(false);
    }

    @Transactional
    public void invite(long communityId, long inviterId, long toUserId) {
        if (!isMember(communityId, inviterId))
            throw ServiceException.forbidden("Only active members can invite");
        if (!users.exists(toUserId))
            throw ServiceException.notFound("Invitee not found");
        if (users.isBlockedEitherWay(inviterId, toUserId))
            throw ServiceException.forbidden("Cannot invite a blocked user");
        if (invites.existsByCommunityIdAndToUserIdAndStatus(communityId, toUserId, CommunityInvite.Status.PENDING))
            throw ServiceException.conflict("Invite already pending");

        invites.save(CommunityInvite.builder()
                .communityId(communityId)
                .fromUserId(inviterId)
                .toUserId(toUserId)
                .status(CommunityInvite.Status.PENDING)
                .sentAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void respond(long inviteId, long responderId, boolean accept) {
        CommunityInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> ServiceException.notFound("Invite not found"));
        if (!inv.getToUserId().equals(responderId))
            throw ServiceException.forbidden("Not your invite");
        if (inv.getStatus() != CommunityInvite.Status.PENDING)
            throw ServiceException.precondition("Already responded");

        if (accept && !members.existsByCommunityIdAndUserId(inv.getCommunityId(), inv.getToUserId())) {
            members.save(CommunityMember.builder()
                    .communityId(inv.getCommunityId())
                    .userId(inv.getToUserId())
                    .joinedAt(LocalDateTime.now())
                    .role(CommunityMember.Role.MEMBER)
                    .build());
        }
        invites.delete(inv);
    }

    public List<CommunityWithRole> myCommunities(long userId) {
        return members.findByUserIdAndRoleNot(userId, CommunityMember.Role.PENDING).stream()
                .map(m -> {
                    Community c = communities.findById(m.getCommunityId()).orElse(null);
                    return c == null ? null : new CommunityWithRole(c, m.getRole());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<Community> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        try {
            return searchIndex.searchIds(keyword, 50).stream()
                    .map(id -> communities.findById(id).orElse(null))
                    .filter(c -> c != null && !Boolean.TRUE.equals(c.getIsPrivate()))
                    .toList();
        } catch (Exception e) {
            return communities.findByNameContainingIgnoreCaseAndIsPrivateFalse(keyword);
        }
    }

    public record CommunityWithRole(Community community, CommunityMember.Role role) {}
}
