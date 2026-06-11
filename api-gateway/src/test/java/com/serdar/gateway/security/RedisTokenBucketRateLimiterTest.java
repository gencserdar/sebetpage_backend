package com.serdar.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisTokenBucketRateLimiterTest {

    @Test
    void failsClosedWhenRedisUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(), any(), any())).thenThrow(new RuntimeException("down"));

        RedisTokenBucketRateLimiter limiter = new RedisTokenBucketRateLimiter(redis);

        assertFalse(limiter.tryAcquire("login:1.2.3.4", 5, 60));
    }
}
