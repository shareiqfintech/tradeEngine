package com.example.trading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A plain {@link StringRedisTemplate} is all this application needs: every
 * key we write (duplicate-signal markers, order locks) is a short-lived
 * string, never a complex object, so there is no reason to pull in a JSON
 * value serializer.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Atomic "delete key only if its value still equals the token I set"
     * script, used by {@code DistributedLockService#unlock} so one thread can
     * never release a lock it does not own (e.g. after its own TTL expired
     * and a different signal already re-acquired the same underlying's lock).
     */
    @Bean
    public RedisScript<Long> unlockScript() {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) "
                + "else return 0 end";
        return new DefaultRedisScript<>(script, Long.class);
    }
}
