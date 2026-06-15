package com.serdar.user.cache;

import com.serdar.user.entity.UserBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlockCacheService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String BY_ME = "blocks:by-me:";
    private static final String BLOCKS_ME = "blocks:blocks-me:";

    private final StringRedisTemplate redis;

    public Set<Long> blockedByMeIds(long userId, Supplier<List<UserBlock>> fallback) {
        String key = BY_ME + userId;
        Set<String> cached = redis.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Long::valueOf).collect(Collectors.toSet());
        }
        List<UserBlock> rows = fallback.get();
        if (rows.isEmpty()) {
            return Set.of();
        }
        String[] values = rows.stream().map(b -> String.valueOf(b.getBlockedId())).toArray(String[]::new);
        redis.opsForSet().add(key, values);
        redis.expire(key, TTL);
        return rows.stream().map(UserBlock::getBlockedId).collect(Collectors.toSet());
    }

    public Set<Long> whoBlocksMeIds(long userId, Supplier<List<Long>> fallback) {
        String key = BLOCKS_ME + userId;
        Set<String> cached = redis.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(Long::valueOf).collect(Collectors.toSet());
        }
        List<Long> rows = fallback.get();
        if (rows.isEmpty()) {
            return Set.of();
        }
        String[] values = rows.stream().map(String::valueOf).toArray(String[]::new);
        redis.opsForSet().add(key, values);
        redis.expire(key, TTL);
        return new HashSet<>(rows);
    }

    public void evict(long blockerId, long blockedId) {
        redis.opsForSet().remove(BY_ME + blockerId, String.valueOf(blockedId));
        redis.opsForSet().remove(BLOCKS_ME + blockedId, String.valueOf(blockerId));
    }

    public void warmBlock(long blockerId, long blockedId) {
        redis.opsForSet().add(BY_ME + blockerId, String.valueOf(blockedId));
        redis.opsForSet().add(BLOCKS_ME + blockedId, String.valueOf(blockerId));
        redis.expire(BY_ME + blockerId, TTL);
        redis.expire(BLOCKS_ME + blockedId, TTL);
    }
}
