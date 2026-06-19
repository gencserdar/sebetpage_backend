package com.serdar.community.repository;

import com.serdar.community.entity.CommunityInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface CommunityInviteRepository extends JpaRepository<CommunityInvite, Long> {
    boolean existsByCommunityIdAndToUserIdAndStatus(Long communityId, Long toUserId, CommunityInvite.Status status);

    @Modifying
    void deleteAllByFromUserIdOrToUserId(Long fromUserId, Long toUserId);
}
