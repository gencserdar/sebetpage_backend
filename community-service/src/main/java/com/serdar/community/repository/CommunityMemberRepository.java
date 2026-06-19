package com.serdar.community.repository;

import com.serdar.community.entity.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Long> {
    boolean existsByCommunityIdAndUserId(Long communityId, Long userId);
    Optional<CommunityMember> findByCommunityIdAndUserId(Long communityId, Long userId);
    List<CommunityMember> findByUserIdAndRoleNot(Long userId, CommunityMember.Role excluded);
    List<CommunityMember> findByCommunityIdAndRoleNot(Long communityId, CommunityMember.Role excluded);

    @Modifying
    void deleteAllByUserId(Long userId);
}
