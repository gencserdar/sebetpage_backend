package com.serdar.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Distributed token-bucket limiter backed by Redis Lua.
 *
 * Redis executes the Lua script atomically, so concurrent gateway instances do
 * not race while consuming or refilling tokens.
 */
@Slf4j
@Component
public class RedisTokenBucketRateLimiter {

    private static final String LUA_SCRIPT =
        "local key        = KEYS[1]\n" +
        "local capacity   = tonumber(ARGV[1])\n" +
        "local refill_ms  = tonumber(ARGV[2])\n" +
        "local now        = tonumber(ARGV[3])\n" +
        "local data       = redis.call('HMGET', key, 'tokens', 'ts')\n" +
        "local tokens     = tonumber(data[1])\n" +
        "local ts         = tonumber(data[2])\n" +
        "if tokens == nil then\n" +
        "  tokens = capacity - 1\n" +
        "  redis.call('HMSET', key, 'tokens', tokens, 'ts', now)\n" +
        "  redis.call('PEXPIRE', key, capacity * refill_ms * 2)\n" +
        "  return 1\n" +
        "end\n" +
        "local elapsed = now - ts\n" +
        "local refill  = math.floor(elapsed / refill_ms)\n" +
        "if refill > 0 then\n" +
        "  tokens = math.min(capacity, tokens + refill)\n" +
        "  ts     = ts + refill * refill_ms\n" +
        "end\n" +
        "if tokens > 0 then\n" +
        "  tokens = tokens - 1\n" +
        "  redis.call('HMSET', key, 'tokens', tokens, 'ts', ts)\n" +
        "  redis.call('PEXPIRE', key, capacity * refill_ms * 2)\n" +
        "  return 1\n" +
        "else\n" +
        "  redis.call('HMSET', key, 'tokens', 0, 'ts', ts)\n" +
        "  redis.call('PEXPIRE', key, refill_ms)\n" +
        "  return 0\n" +
        "end\n";

    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setScriptText(LUA_SCRIPT);
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public RedisTokenBucketRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, long capacity, long windowSeconds) {
        return tryAcquireMillis(key, capacity, refillMillis(capacity, windowSeconds));
    }

    public boolean tryAcquireMillis(String key, long capacity, long refillMillis) {
        try {
            Long result = redis.execute(
                    SCRIPT,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillMillis),
                    String.valueOf(System.currentTimeMillis())
            );
            return !Long.valueOf(0L).equals(result);
        } catch (Exception e) {
            log.error("Redis rate-limit unavailable for key '{}', failing closed: {}", key, e.getMessage());
            return false;
        }
    }

    public static long refillMillis(long capacity, long windowSeconds) {
        if (capacity <= 0) throw new IllegalStateException("Rate limit capacity must be positive");
        if (windowSeconds <= 0) throw new IllegalStateException("Rate limit window must be positive");
        return Math.max(1L, Math.multiplyExact(windowSeconds, 1000L) / capacity);
    }
}
