package com.example.trading.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SignalDeduplicationServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SignalDeduplicationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new SignalDeduplicationService(redisTemplate);
    }

    @Test
    void markIfFirstSeen_firstTime_returnsTrue_andSetsKeyWith24hTtl() {
        when(valueOperations.setIfAbsent(eq("trading:signal:NIFTY-001"), any(), eq(Duration.ofHours(24))))
                .thenReturn(true);

        boolean result = service.markIfFirstSeen("NIFTY-001");

        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(eq("trading:signal:NIFTY-001"), any(), eq(Duration.ofHours(24)));
    }

    @Test
    void markIfFirstSeen_duplicate_returnsFalse() {
        when(valueOperations.setIfAbsent(eq("trading:signal:NIFTY-001"), any(), any(Duration.class)))
                .thenReturn(false);

        boolean result = service.markIfFirstSeen("NIFTY-001");

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_delegatesToHasKey() {
        when(redisTemplate.hasKey("trading:signal:NIFTY-002")).thenReturn(true);

        assertThat(service.isDuplicate("NIFTY-002")).isTrue();
    }
}
