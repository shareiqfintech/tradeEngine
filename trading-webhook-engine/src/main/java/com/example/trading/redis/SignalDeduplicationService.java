package com.example.trading.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis-backed duplicate-signal protection.
 *
 * <p>Key: {@code trading:signal:{signalId}}, TTL 24h. TradingView (or a flaky
 * network / retried alert) can send the exact same {@code signalId} more
 * than once; the first caller to successfully {@code SETNX} the key "owns"
 * that signal for the next 24 hours - every subsequent arrival of the same
 * id is a duplicate and must never reach order placement again.
 */
@Slf4j
@Service
public class SignalDeduplicationService {

    private static final String KEY_PREFIX = "trading:signal:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public SignalDeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically marks {@code signalId} as seen.
     *
     * @return {@code true} if this call is the first to see the signal
     *         (caller should proceed), {@code false} if it is a duplicate
     *         (caller must reject/ignore).
     */
    public boolean markIfFirstSeen(String signalId) {
        String key = KEY_PREFIX + signalId;
        Boolean firstSeen = redisTemplate.opsForValue().setIfAbsent(key, Instant.now().toString(), TTL);
        boolean result = Boolean.TRUE.equals(firstSeen);
        if (!result) {
            log.info("DUPLICATE_SIGNAL signalId={}", signalId);
        }
        return result;
    }

    public boolean isDuplicate(String signalId) {
        String key = KEY_PREFIX + signalId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
