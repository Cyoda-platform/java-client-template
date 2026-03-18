package com.java_template.application.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Authorization filter for TMS API endpoints
 * Validates JWT tokens and sets user context
 * Can be disabled via app.auth.filter.enabled=false
 */
public class AuthorizationFilter implements Filter {
    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;

    public AuthorizationFilter(JwtTokenProvider tokenProvider, AuthService authService) {
        if (tokenProvider == null || authService == null) {
            throw new IllegalArgumentException("tokenProvider and authService must not be null");
        }
        this.tokenProvider = tokenProvider;
        this.authService = authService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Skip auth for login endpoint
        if (path.contains("/login")) {
            chain.doFilter(request, response);
            return;
        }

        // Skip auth for health/actuator endpoints
        if (path.contains("/actuator") || path.contains("/health")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);
        String username = tokenProvider.validateAndGetUsername(token);

        if (username == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return;
        }

        // Set user context in request
        httpRequest.setAttribute("username", username);
        chain.doFilter(request, response);
    }
}

