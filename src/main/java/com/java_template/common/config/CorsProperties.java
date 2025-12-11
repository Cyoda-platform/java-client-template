package com.java_template.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * ABOUTME: Configuration properties for CORS settings with secure defaults.
 * Supports environment-specific configuration via application.yml and environment variables.
 *
 * <p>Security considerations:
 * <ul>
 *   <li>Never uses wildcards with credentials enabled</li>
 *   <li>Requires explicit origin configuration for production</li>
 *   <li>Restricts headers to only what's needed by default</li>
 *   <li>Supports preflight caching for performance</li>
 * </ul>
 *
 * <p>Environment variables (via application.yml mapping):
 * <ul>
 *   <li>CORS_ALLOWED_ORIGINS - Comma-separated list of allowed origins</li>
 *   <li>CORS_ALLOWED_METHODS - Comma-separated list of allowed HTTP methods</li>
 *   <li>CORS_ALLOWED_HEADERS - Comma-separated list of allowed headers</li>
 *   <li>CORS_EXPOSED_HEADERS - Comma-separated list of headers exposed to client</li>
 *   <li>CORS_ALLOW_CREDENTIALS - Whether to allow credentials (true/false)</li>
 *   <li>CORS_MAX_AGE_SECONDS - Preflight cache duration in seconds</li>
 * </ul>
 */
@SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed"})
@ConfigurationProperties(prefix = "app.cors")
@Validated
public class CorsProperties {

    /**
     * List of allowed origins for CORS requests.
     * Must be explicitly configured - wildcards are not recommended with credentials.
     * Example: ["http://localhost:3000", "https://app.example.com"]
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    @NotEmpty(message = "At least one allowed origin must be configured")
    private List<String> allowedOrigins = List.of("http://localhost:3000");

    /**
     * List of allowed HTTP methods for CORS requests.
     * Defaults to common REST methods.
     */
    @NotEmpty(message = "At least one allowed method must be configured")
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

    /**
     * List of allowed headers in CORS requests.
     * Defaults to commonly needed headers for REST APIs.
     */
    @NotEmpty(message = "At least one allowed header must be configured")
    private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With",
            "Cache-Control"
    );

    /**
     * List of headers exposed to the client in CORS responses.
     * Empty by default - only expose headers that clients need.
     */
    private List<String> exposedHeaders = List.of();

    /**
     * Whether to allow credentials (cookies, authorization headers) in CORS requests.
     * When true, allowedOrigins cannot contain wildcards.
     * Defaults to false for security.
     */
    private boolean allowCredentials = false;

    /**
     * How long (in seconds) browsers should cache preflight request results.
     * Higher values reduce preflight requests but delay configuration changes.
     * Default: 3600 seconds (1 hour) - suitable for production.
     * Range: 0-86400 (0 to 24 hours)
     */
    @Min(value = 0, message = "Max age must be at least 0 seconds")
    @Max(value = 86400, message = "Max age cannot exceed 86400 seconds (24 hours)")
    private long maxAgeSeconds = 3600;

    /**
     * URL pattern to apply CORS configuration to.
     * Defaults to all paths.
     */
    private String pathPattern = "/**";

    // Getters and Setters

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    /**
     * Validates that the configuration is secure.
     * Called after properties are bound.
     *
     * @throws IllegalStateException if configuration is insecure
     */
    public void validateSecurityConstraints() {
        if (allowCredentials) {
            boolean hasWildcard = allowedOrigins.stream()
                    .anyMatch(origin -> "*".equals(origin) || origin.contains("*"));
            if (hasWildcard) {
                throw new IllegalStateException(
                        "CORS configuration error: Cannot use wildcard origins when credentials are enabled. " +
                        "Either disable credentials or specify explicit origins."
                );
            }
        }
    }
}

