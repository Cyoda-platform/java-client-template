package com.java_template.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * ABOUTME: Spring configuration class that provides WebClient beans for making
 * HTTP requests to external services, including the Cyoda entity search API.
 */
@Configuration
public class WebClientConfig {

    /**
     * Provides a WebClient.Builder bean for creating WebClient instances.
     * This builder can be used to make reactive HTTP requests to external APIs.
     *
     * @return WebClient.Builder configured with default settings
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Provides a pre-configured WebClient bean for general-purpose HTTP requests.
     * Uses the WebClient.Builder to create a default instance.
     *
     * @param builder the WebClient.Builder bean
     * @return WebClient instance
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}

