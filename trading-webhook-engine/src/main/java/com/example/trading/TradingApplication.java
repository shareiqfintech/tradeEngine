package com.example.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the trading-webhook-engine application.
 *
 * <p>This application is designed to run continuously (24/7). It must never be
 * shut down after market close - the {@code TradingStateService} (introduced in
 * a later phase) is responsible for enabling/disabling trading based on market
 * hours, not the JVM lifecycle.
 */
@SpringBootApplication
public class TradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}
