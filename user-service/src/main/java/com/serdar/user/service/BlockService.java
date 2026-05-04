package com.serdar.user.service;

import com.serdar.common.ServiceException;
import com.serdar.user.entity.FriendRequest;
import com.serdar.user.entity.Friendship;
import com.serdar.user.entity.UserBlock;
import com.serdar.user.repository.FriendRequestRepository;
import com.serdar.user.repository.FriendshipRepository;
import com.serdar.user.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Blocks are strictly directional in storage, but most predicates care about
 * "either direction" (you and the other party shouldn't interact if either
 * blocked the other). Blocking also cleans up the existing friendship and any
 * pending friend-requests in both directions.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;

    @Transactional
    public void block(long blockerId, long blockedId) {
        if (blockerId == blockedId) throw ServiceException.invalid("You cannot block yourself");
        if (blocks.existsByBlockerIdAndBlockedId(blockerId, blockedId)) return;

        blocks.save(UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .createdAt(LocalDateTime.now())
                .build());

        // Tear down friendship if present.
        friendships.findByUsers(blockerId, blockedId).ifPresent(friendships::delete);

        // Drop any pending requests between the two.
        requests.findByFromUserIdAndToUserIdAndStatus(blockerId, blockedId, FriendRequest.Status.PENDING)
                .ifPresent(requests::delete);
        requests.findByFromUserIdAndToUserIdAndStatus(blockedId, blockerId, FriendRequest.Status.PENDING)
                .ifPresent(requests::delete);
    }

    @Transactional
    public void unblock(long blockerId, long blockedId) {
        blocks.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public boolean blockedByMe(long me, long other) {
        return blocks.existsByBlockerIdAndBlockedId(me, other);
    }

    public boolean blocksMe(long me, long other) {
        return blocks.existsByBlockerIdAndBlockedId(other, me);
    }

    public boolean eitherWay(long a, long b) {
        return blockedByMe(a, b) || blocksMe(a, b);
    }

    public List<UserBlock> myBlocks(long blockerId) {
        return blocks.findAllByBlockerId(blockerId);
    }

    public List<Long> whoBlocksMe(long userId) {
        return blocks.findAllBlockerIdsOf(userId);
    }

    /** Convenience delete used when friendship removal already handled elsewhere. */
    public Optional<UserBlock> find(long blockerId, long blockedId) {
        return blocks.findByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    public void removeFriendshipIfAny(Friendship f) {
        friendships.delete(f);
    }
}
