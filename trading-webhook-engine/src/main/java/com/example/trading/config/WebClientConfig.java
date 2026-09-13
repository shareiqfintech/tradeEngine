package com.example.trading.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    public static final String GROWW_API_CLIENT = "growwApiWebClient";
    public static final String GROWW_ASSET_CLIENT = "growwAssetWebClient";

    /** For JSON API calls (auth, orders, positions) - short timeouts, small payloads. */
    @Bean(GROWW_API_CLIENT)
    public WebClient growwApiWebClient(TradingProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(properties.getGroww().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** For downloading the (potentially large) instrument master CSV - longer read timeout. */
    @Bean(GROWW_ASSET_CLIENT)
    public WebClient growwAssetWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(config -> config.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
    }
}
