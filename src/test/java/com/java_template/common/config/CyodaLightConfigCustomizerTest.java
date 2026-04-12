package com.java_template.common.config;

// ABOUTME: Verifies CyodaLightConfigCustomizer injects the correct property overrides
// into the Spring Environment when the cyoda-light toggle is active.

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that CyodaLightConfigCustomizer installs a high-priority property source
 * that rewrites app.config.* platform endpoints to target the cyoda-light sidecar
 * whenever {@code app.config.cyoda-light.active=true}.
 */
class CyodaLightConfigCustomizerTest {

    private final CyodaLightConfigCustomizer customizer = new CyodaLightConfigCustomizer();

    @Test
    void postProcessEnvironment_whenActive_overridesPlatformEndpointsWithDefaults() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.config.cyoda-light.active", "true");

        customizer.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("app.config.cyoda-api-url")).isEqualTo("http://cyoda-light:8080/api");
        assertThat(environment.getProperty("app.config.grpc-address")).isEqualTo("cyoda-light");
        assertThat(environment.getProperty("app.config.grpc-server-port", Integer.class)).isEqualTo(50051);
        assertThat(environment.getProperty("app.config.skip-ssl", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.config.ssl-trust-all", Boolean.class)).isTrue();
    }

    @Test
    void postProcessEnvironment_whenActive_overridesPlatformEndpointsWithCustomValues() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.config.cyoda-light.active", "true");
        environment.setProperty("app.config.cyoda-light.http-url", "http://my-custom-host:9090");
        environment.setProperty("app.config.cyoda-light.grpc-host", "my-custom-host");
        environment.setProperty("app.config.cyoda-light.grpc-port", "9999");

        customizer.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("app.config.cyoda-api-url")).isEqualTo("http://my-custom-host:9090/api");
        assertThat(environment.getProperty("app.config.grpc-address")).isEqualTo("my-custom-host");
        assertThat(environment.getProperty("app.config.grpc-server-port", Integer.class)).isEqualTo(9999);
    }

    @Test
    void postProcessEnvironment_whenInactive_leavesEnvironmentUntouched() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.config.cyoda-api-url", "https://prod.cyoda.net/api");
        environment.setProperty("app.config.grpc-address", "grpc-prod.cyoda.net");
        environment.setProperty("app.config.grpc-server-port", "443");

        customizer.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("app.config.cyoda-api-url")).isEqualTo("https://prod.cyoda.net/api");
        assertThat(environment.getProperty("app.config.grpc-address")).isEqualTo("grpc-prod.cyoda.net");
        assertThat(environment.getProperty("app.config.grpc-server-port", Integer.class)).isEqualTo(443);
        assertThat(environment.getPropertySources().contains("cyodaLightOverrides")).isFalse();
    }
}
