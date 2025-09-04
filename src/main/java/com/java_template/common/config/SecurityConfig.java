package com.java_template.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ABOUTME: Spring Security configuration disabling authentication for local development
 * allowing unrestricted access to Swagger UI and all application endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
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
}
