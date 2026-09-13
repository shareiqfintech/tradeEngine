package com.example.trading.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed mutual-exclusion lock, key {@code trading:order-lock:{underlying}}.
 *
 * <p>Prevents two signals for the same underlying (e.g. a BUY and a SELL
 * arriving within milliseconds of each other, or the same signal replayed by
 * a retrying webhook sender before its Redis dedup key existed) from being
 * processed by {@code TradingEngineService} concurrently and racing each
 * other to check positions / place orders.
 */
@Slf4j
@Service
public class DistributedLockService {

    private static final String KEY_PREFIX = "trading:order-lock:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> unlockScript;

    public DistributedLockService(StringRedisTemplate redisTemplate, RedisScript<Long> unlockScript) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = unlockScript;
    }

    /**
     * Attempts to acquire the lock for {@code underlying}.
     *
     * @return an opaque lock token to pass to {@link #unlock} if acquired,
     *         or {@link Optional#empty()} if another thread/process already
     *         holds the lock.
     */
    public Optional<String> tryLock(String underlying, Duration ttl) {
        String key = KEY_PREFIX + underlying;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(token);
        }
        log.warn("ORDER_LOCK_BUSY underlying={}", underlying);
        return Optional.empty();
    }

    /** Releases the lock only if {@code token} still matches - see {@code unlockScript}. */
    public void unlock(String underlying, String token) {
        String key = KEY_PREFIX + underlying;
        redisTemplate.execute(unlockScript, Collections.singletonList(key), token);
    }
}
