package com.java_template.common.config;

// ABOUTME: Redirects platform communication at the cyoda-light in-memory digital twin by
// injecting property overrides into the Spring Environment before @ConfigurationProperties
// binding runs, so Config is bound directly to the sidecar's endpoints with no post-hoc mutation.

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot {@link EnvironmentPostProcessor} that rewrites the platform connection
 * properties to target the cyoda-light in-memory digital twin when the feature toggle
 * {@code app.config.cyoda-light.active=true} is set.
 *
 * <p>Running as an {@code EnvironmentPostProcessor} — registered in
 * {@code META-INF/spring.factories} — ensures the overrides are applied <em>before</em>
 * any {@code @ConfigurationProperties} bean is bound. {@link Config} therefore sees the
 * cyoda-light endpoints directly; no bean mutation or ordering dependency is required.
 *
 * <p>The overrides are installed as a high-priority {@link MapPropertySource} so they win
 * over application.yml defaults while still being overridable by an explicit environment
 * variable (env vars sit in {@code systemEnvironment} which has higher precedence).
 */
public class CyodaLightConfigCustomizer implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CyodaLightConfigCustomizer.class);

    private static final String ACTIVE_PROPERTY = "app.config.cyoda-light.active";
    private static final String PROPERTY_SOURCE_NAME = "cyodaLightOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Boolean active = environment.getProperty(ACTIVE_PROPERTY, Boolean.class, Boolean.FALSE);
        if (!Boolean.TRUE.equals(active)) {
            return;
        }

        String httpUrl = environment.getProperty(
                "app.config.cyoda-light.http-url", "http://cyoda-light:8080");
        String grpcHost = environment.getProperty(
                "app.config.cyoda-light.grpc-host", "cyoda-light");
        int grpcPort = environment.getProperty(
                "app.config.cyoda-light.grpc-port", Integer.class, 50051);

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("app.config.cyoda-api-url", httpUrl + "/api");
        overrides.put("app.config.grpc-address", grpcHost);
        overrides.put("app.config.grpc-server-port", grpcPort);
        overrides.put("app.config.skip-ssl", true);
        overrides.put("app.config.ssl-trust-all", true);

        // addFirst ensures these overrides take precedence over application.yml, but
        // systemEnvironment / command-line args (which sit in earlier sources) still win.
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));

        log.info("cyoda-light active — targeting HTTP={}, gRPC={}:{}", httpUrl, grpcHost, grpcPort);
    }

    @Override
    public int getOrder() {
        // Run after Spring Boot's ConfigDataEnvironmentPostProcessor so application.yml
        // is already on the environment and we can read the toggle from it.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
