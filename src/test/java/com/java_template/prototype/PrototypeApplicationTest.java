package com.java_template.prototype;

import com.java_template.prototype.config.MockAuthenticationConfig;
import com.java_template.prototype.config.MockGrpcConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
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
 * <li>Swagger UI: <a href="http://localhost:8081/swagger-ui/index.html">http://localhost:8081/swagger-ui/index.html</a></li>
 * <li>API Docs: <a href="http://localhost:8081/v3/api-docs">http://localhost:8081/v3/api-docs</a></li>
 * <li>Base URL: <a href="http://localhost:8081">http://localhost:8081</a></li>
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
    "server.port=8081",
    "logging.level.com.java_template.prototype=DEBUG",
    "prototype.enabled=true",
    "spring.profiles.active=prototype"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PrototypeApplicationTest {

    @Configuration
    @ComponentScan(
            basePackages = {
                    "com.java_template.prototype",
                    "com.java_template.application",
                    "com.java_template.common"
            },
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.java_template.common.grpc.*|com.java_template.common.auth.*|com.java_template.common.service.*")
    )
    @EnableAutoConfiguration(exclude = {
        OAuth2ClientAutoConfiguration.class
    })
    @Import({MockAuthenticationConfig.class, MockGrpcConfig.class})
    static class PrototypeConfig {
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
        System.out.println("📍 Swagger UI: http://localhost:8081/swagger-ui/index.html");
        System.out.println("📍 API Docs: http://localhost:8081/v3/api-docs");
        System.out.println("📍 Base URL: http://localhost:8081");
        System.out.println("🛑 Press Ctrl+C to stop");

        // Keep the application running indefinitely
        Thread.currentThread().join();
    }
}

