package com.java_template.prototype.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for EntityControllerPrototype tests.
 * Provides mocked beans for testing purposes.
 */
@TestConfiguration
public class EntityControllerPrototypeTestConfiguration {

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return mock(RestTemplate.class);
    }
}
