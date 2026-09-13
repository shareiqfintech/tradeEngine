package com.example.trading.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DistributedLockServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisScript<Long> unlockScript;
    private DistributedLockService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        unlockScript = mock(RedisScript.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DistributedLockService(redisTemplate, unlockScript);
    }

    @Test
    void tryLock_whenFree_returnsToken() {
        when(valueOperations.setIfAbsent(eq("trading:order-lock:NIFTY"), anyString(), any(Duration.class)))
                .thenReturn(true);

        Optional<String> token = service.tryLock("NIFTY", Duration.ofSeconds(30));

        assertThat(token).isPresent();
    }

    @Test
    void tryLock_whenBusy_returnsEmpty() {
        when(valueOperations.setIfAbsent(eq("trading:order-lock:NIFTY"), anyString(), any(Duration.class)))
                .thenReturn(false);

        Optional<String> token = service.tryLock("NIFTY", Duration.ofSeconds(30));

        assertThat(token).isEmpty();
    }

    @Test
    void unlock_executesUnlockScriptWithKeyAndToken() {
        service.unlock("NIFTY", "token-123");

        verify(redisTemplate).execute(eq(unlockScript), eq(java.util.Collections.singletonList("trading:order-lock:NIFTY")), eq("token-123"));
    }
}
