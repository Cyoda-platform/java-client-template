package com.java_template.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * ABOUTME: Spring Boot auto-configuration providing permissive security and CORS defaults.
 * Active only when no application-defined {@code SecurityFilterChain} / {@code CorsConfigurationSource} bean is present.
 *
 * <p>This class is registered as auto-configuration so it is evaluated <em>after</em> all
 * {@code @Configuration} beans in the application context. The {@code @ConditionalOnMissingBean}
 * guards therefore work reliably: if the application supplies its own chain (e.g.
 * {@code ApiSecurityConfig}) or its own CORS source, these defaults are silently skipped.
 *
 * <p>Default security policy: permit-all — suitable for local PoC development.
 * Override by providing a {@code SecurityFilterChain} bean in your application package.
 *
 * <p>Default CORS policy: driven by {@code app.cors.*} properties (see {@link CorsProperties}).
 * Override by providing a {@code CorsConfigurationSource} bean in your application package.
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @PostConstruct
    public void init() {
        // Validate security constraints on startup
        corsProperties.validateSecurityConstraints();
        logger.info("CORS configuration initialized - allowed origins: {}, credentials: {}",
                corsProperties.getAllowedOrigins(),
                corsProperties.isAllowCredentials());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS with custom configuration
            .cors(Customizer.withDefaults())

            // Disable CSRF protection
            .csrf(AbstractHttpConfigurer::disable)

            // Disable form login
            .formLogin(AbstractHttpConfigurer::disable)

            // Disable HTTP Basic authentication
            .httpBasic(AbstractHttpConfigurer::disable)

            // Disable OAuth2 login for web endpoints (but keep OAuth2 client for gRPC)
            .oauth2Login(AbstractHttpConfigurer::disable)

            // Allow all requests without authentication
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Set allowed origins (explicit list, no wildcards when credentials enabled)
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // Set allowed HTTP methods
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());

        // Set allowed headers (restricted to what's needed)
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // Set exposed headers (what client JavaScript can access)
        if (corsProperties.getExposedHeaders() != null && !corsProperties.getExposedHeaders().isEmpty()) {
            configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        }

        // Set credentials policy
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());

        // Set preflight cache duration
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(corsProperties.getPathPattern(), configuration);

        logger.debug("CORS configuration applied - origins: {}, methods: {}, headers: {}, credentials: {}, maxAge: {}s",
                corsProperties.getAllowedOrigins(),
                corsProperties.getAllowedMethods(),
                corsProperties.getAllowedHeaders(),
                corsProperties.isAllowCredentials(),
                corsProperties.getMaxAgeSeconds());

        return source;
    }
}
