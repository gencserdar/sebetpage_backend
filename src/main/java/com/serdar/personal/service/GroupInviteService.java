package com.serdar.personal.service;

import com.serdar.personal.model.GroupInvite;
import com.serdar.personal.model.GroupMember;
import com.serdar.personal.repository.GroupInviteRepository;
import com.serdar.personal.repository.GroupMemberRepository;
import com.serdar.personal.repository.GroupRepository;
import com.serdar.personal.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GroupInviteService {

    private final GroupInviteRepository inviteRepo;
    private final GroupMemberRepository memberRepo;
    private final GroupRepository groupRepo;
    private final UserRepository userRepo;

    @Transactional
    public void invite(Long groupId, String inviterEmail, Long toUserId) throws AccessDeniedException {
        var inviter = userRepo.findByEmail(inviterEmail).orElseThrow();
        var gm = memberRepo.findByGroupIdAndUserId(groupId, inviter.getId())
                .orElseThrow(() -> new AccessDeniedException("Not a member"));
        if (gm.getRole() == GroupMember.Role.PENDING)
            throw new AccessDeniedException("Pending member cannot invite");

        if (inviteRepo.existsByGroupIdAndToUserIdAndStatus(groupId, toUserId, GroupInvite.InviteStatus.PENDING))
            return;

        var invite = GroupInvite.builder()
                .fromUser(inviter)
                .toUser(userRepo.findById(toUserId).orElseThrow())
                .group(groupRepo.findById(groupId).orElseThrow())
                .status(GroupInvite.InviteStatus.PENDING)
                .sentAt(LocalDateTime.now())
                .build();
        inviteRepo.save(invite);
    }

    @Transactional
    public void respond(Long inviteId, String responderEmail, boolean accept) throws AccessDeniedException {
        var responder = userRepo.findByEmail(responderEmail).orElseThrow();
        var inv = inviteRepo.findById(inviteId).orElseThrow();
        if (!inv.getToUser().getId().equals(responder.getId()))
            throw new AccessDeniedException("Not your invite");

        inv.setStatus(accept ? GroupInvite.InviteStatus.ACCEPTED : GroupInvite.InviteStatus.REJECTED);
        inviteRepo.save(inv);

        if (accept && !memberRepo.existsByGroupIdAndUserId(inv.getGroup().getId(), responder.getId())) {
            memberRepo.save(GroupMember.builder()
                    .group(inv.getGroup()).user(responder)
                    .role(GroupMember.Role.MEMBER)
                    .joinedAt(LocalDateTime.now())
                    .build());
        }
    }
}
