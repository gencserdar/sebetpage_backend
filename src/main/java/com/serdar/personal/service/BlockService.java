package com.serdar.personal.service;

import com.serdar.personal.model.User;
import com.serdar.personal.model.UserBlock;
import com.serdar.personal.repository.FriendRequestRepository;
import com.serdar.personal.repository.FriendshipRepository;
import com.serdar.personal.repository.UserBlockRepository;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BlockService {
    private final UserBlockRepository blockRepo;
    private final UserRepository userRepo;
    private final FriendshipRepository friendshipRepository;    // opsiyonel: blokta friend’i düşürmek için
    private final FriendRequestRepository friendRequestRepository; // opsiyonel: pending istekleri iptal için

    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (Objects.equals(blockerId, blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        if (blockRepo.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return; // idempotent
        }

        User blocker = userRepo.findById(blockerId).orElseThrow();
        User blocked = userRepo.findById(blockedId).orElseThrow();

        // Arkadaşlığı kaldır (varsa)
        friendshipRepository.findByUsers(blockerId, blockedId)
                .ifPresent(friendshipRepository::delete);

        // Bekleyen friend request’leri temizle (iki yön)
        friendRequestRepository.findByFromUserAndToUser(blocker, blocked)
                .ifPresent(friendRequestRepository::delete);
        friendRequestRepository.findByFromUserAndToUser(blocked, blocker)
                .ifPresent(friendRequestRepository::delete);

        blockRepo.save(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build());
    }


    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        blockRepo.deleteByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedByMe(Long meId, Long otherId) {
        return blockRepo.existsByBlockerIdAndBlockedId(meId, otherId);
    }

    @Transactional(readOnly = true)
    public boolean blocksMe(Long meId, Long otherId) {
        return blockRepo.existsByBlockerIdAndBlockedId(otherId, meId);
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long a, Long b) {
        return blockRepo.existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(a, b, b, a);
    }

    public List<UserBlock> myBlocks(Long meId) {
        return blockRepo.findAllByBlockerId(meId);
    }

    /** Beni engelleyenler */
    @Transactional(readOnly = true)
    public List<Long> whoBlocksMeIds(Long meId) {
        return blockRepo.findAllBlockerIdsOf(meId);
    }
}

