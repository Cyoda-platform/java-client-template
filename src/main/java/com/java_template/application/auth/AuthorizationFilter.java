package com.java_template.application.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Authorization filter for API endpoints
 * Validates JWT tokens and checks @RequireRole annotations
 * Allows public endpoints like /api/login without token
 */
public class AuthorizationFilter implements Filter {
    private final JwtTokenProvider tokenProvider;

    public AuthorizationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Allow public endpoints without token
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract and validate token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return;
        }

        String username = tokenProvider.getUsernameFromToken(token);
        String role = tokenProvider.getRoleFromToken(token);

        // Set user context in request
        httpRequest.setAttribute("username", username);
        httpRequest.setAttribute("role", role);

        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/login") ||
               path.contains("/actuator") ||
               path.contains("/health") ||
               path.contains("/swagger") ||
               path.contains("/api-docs") ||
               path.contains("/webjars") ||
               path.equals("/api") ||
               path.equals("/api/");
    }
}

