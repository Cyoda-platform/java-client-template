package com.java_template.prototype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;

/**
 * ABOUTME: Test-based prototype application runner for interactive development and API exploration.
 * <p>
 * This test launches a full Spring Boot application with only the prototype controller
 * and entity classes, excluding all common module dependencies.
 * <p>
 * To run: ./gradlew test --tests PrototypeApplicationTest -Dprototype.enabled=true
 * <p>
 * Access via:
 * <ul>
 * <li>Swagger UI: <a href="http://localhost:8080/swagger-ui/index.html">http://localhost:8080/swagger-ui/index.html</a></li>
 * <li>API Docs: <a href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a></li>
 * <li>Base URL: <a href="http://localhost:8080">http://localhost:8080</a></li>
 * </ul>
 * <p>
 * Note: This test is disabled by default to prevent it from running during normal test execution.
 * It only runs when the system property 'prototype.enabled' is set to 'true'.
 */
@SpringBootTest(
    classes = PrototypeApplicationTest.PrototypeConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@TestPropertySource(properties = {
    "server.port=8080",
    "logging.level.com.java_template.prototype=DEBUG",
    "spring.profiles.active=prototype-test"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PrototypeApplicationTest {

    @SuppressWarnings("SpringComponentScan")  // entity packages shows up in concrete implementations of this template
    @Configuration
    @ComponentScan(basePackages = {
        "com.java_template.prototype",
        "com.java_template.application.entity"
    })
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
        OAuth2ClientAutoConfiguration.class
    })
    static class PrototypeConfig {
        // This configuration only loads prototype and entity packages
        // All common module beans are excluded
        // Entities can still use common module interfaces - that's fine
    }

    /**
     * Checks if the prototype is enabled via system property.
     * This method is used by @EnabledIf to conditionally run the test.
     */
    static boolean isPrototypeEnabled() {
        return "true".equals(System.getProperty("prototype.enabled"));
    }

    @Test
    @EnabledIf("isPrototypeEnabled")
    void runPrototypeApplication() throws InterruptedException {
        System.out.println("🚀 Prototype Application Started!");
        System.out.println("📍 Swagger UI: http://localhost:8080/swagger-ui/index.html");
        System.out.println("📍 API Docs: http://localhost:8080/v3/api-docs");
        System.out.println("📍 Base URL: http://localhost:8080");
        System.out.println("🛑 Press Ctrl+C to stop");

        // Keep the application running indefinitely
        Thread.currentThread().join();
    }
}

