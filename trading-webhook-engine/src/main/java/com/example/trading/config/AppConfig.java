package com.example.trading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * Enables async signal processing ({@code TradingEngineService} runs off the
 * webhook request thread) and the cron-based schedulers (morning auth,
 * trading window, reconciliation), and binds {@link TradingProperties}.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(TradingProperties.class)
public class AppConfig {

    public static final String TRADING_EXECUTOR = "tradingExecutor";

    /**
     * The single source of "now" for every time-of-day decision (session
     * start / 15:10 cutoff / 15:30 close, weekday checks, restart recovery).
     * Pinned to {@code trading.timezone} (Asia/Kolkata) so the rules are
     * correct no matter what the host/EC2 zone is (typically UTC). Tests
     * inject {@code Clock.fixed(...)} to pin the instant.
     */
    @Bean
    public Clock tradingClock(TradingProperties properties) {
        return Clock.system(properties.getZoneId());
    }

    @Bean(name = TRADING_EXECUTOR)
    public Executor tradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("trading-engine-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
