package com.java_template.application.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * ApplicationConfig - Configuration for application-specific beans
 * 
 * This configuration provides:
 * - RestTemplate for external API calls
 * - HTTP client configuration with timeouts
 * - Other application-specific beans
 */
@Configuration
public class ApplicationConfig {

    @Value("${http.client.connect-timeout:5000}")
    private int connectTimeout;
    
    @Value("${http.client.read-timeout:10000}")
    private int readTimeout;

    /**
     * RestTemplate bean for making HTTP requests to external APIs
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(clientHttpRequestFactory());
        return restTemplate;
    }

    /**
     * HTTP client request factory with configured timeouts
     */
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
