package com.prism.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;


import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean("alphaWebClient")
    public WebClient alphaWebClient(@Value("${prism.providers.alpha.timeout}") Duration timeout) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) timeout.toMillis()
                );

        return WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .build();
    }

    @Bean("betaWebClient")
    public WebClient betaWebClient(
            @Value("${prism.providers.beta.timeout}") Duration timeout) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) timeout.toMillis()
                );

        return WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient)
                )
                .build();
    }
}
