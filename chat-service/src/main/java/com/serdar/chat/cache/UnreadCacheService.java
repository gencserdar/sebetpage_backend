package com.serdar.chat.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
public class UnreadCacheService {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    private String convKey(long userId, long conversationId) {
        return "chat:unread:" + userId + ":" + conversationId;
    }

    private String totalKey(long userId) {
        return "chat:unread:total:" + userId;
    }

    public void increment(long userId, long conversationId) {
        redis.opsForValue().increment(convKey(userId, conversationId));
        redis.opsForValue().increment(totalKey(userId));
        touchTtl(userId, conversationId);
    }

    public void resetConversation(long userId, long conversationId, int previousUnread) {
        clearConversation(userId, conversationId);
    }

    public void clearConversation(long userId, long conversationId) {
        String key = convKey(userId, conversationId);
        String prev = redis.opsForValue().get(key);
        int n = prev == null ? 0 : Math.max(0, Integer.parseInt(prev));
        redis.delete(key);
        if (n > 0) {
            Long total = redis.opsForValue().increment(totalKey(userId), -n);
            if (total != null && total <= 0) {
                redis.delete(totalKey(userId));
            }
        }
    }

    public void setConversationUnread(long userId, long conversationId, int count) {
        if (count <= 0) {
            redis.delete(convKey(userId, conversationId));
            return;
        }
        redis.opsForValue().set(convKey(userId, conversationId), String.valueOf(count));
        touchTtl(userId, conversationId);
    }

    public void setTotalUnread(long userId, int total) {
        if (total <= 0) {
            redis.delete(totalKey(userId));
            return;
        }
        redis.opsForValue().set(totalKey(userId), String.valueOf(total));
        redis.expire(totalKey(userId), TTL);
    }

    public int getConversationUnread(long userId, long conversationId, IntSupplier fallback) {
        String v = redis.opsForValue().get(convKey(userId, conversationId));
        if (v != null) {
            return Math.max(0, Integer.parseInt(v));
        }
        int computed = Math.max(0, fallback.getAsInt());
        if (computed > 0) {
            redis.opsForValue().set(convKey(userId, conversationId), String.valueOf(computed));
            touchTtl(userId, conversationId);
        }
        return computed;
    }

    public int getTotalUnread(long userId, IntSupplier fallback) {
        String v = redis.opsForValue().get(totalKey(userId));
        if (v != null) {
            return Math.max(0, Integer.parseInt(v));
        }
        int computed = Math.max(0, fallback.getAsInt());
        redis.opsForValue().set(totalKey(userId), String.valueOf(computed));
        redis.expire(totalKey(userId), TTL);
        return computed;
    }

    public void warmTotal(long userId, int total) {
        setTotalUnread(userId, total);
    }

    private void touchTtl(long userId, long conversationId) {
        redis.expire(convKey(userId, conversationId), TTL);
        redis.expire(totalKey(userId), TTL);
    }
}
