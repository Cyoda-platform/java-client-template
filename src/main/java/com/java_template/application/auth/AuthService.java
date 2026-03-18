package com.java_template.application.auth;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication service with hardcoded users
 * Supports ADMIN and TESTER roles
 */
@Service
public class AuthService {
    private final JwtTokenProvider tokenProvider;
    private final Map<String, User> users = new HashMap<>();

    public AuthService(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        // Initialize hardcoded users
        users.put("admin", new User("admin", "admin123", "ADMIN"));
        users.put("tester", new User("tester", "tester123", "TESTER"));
    }

    /**
     * Authenticate user and return LoginResponse with token
     */
    public LoginResponse authenticate(String username, String password) {
        User user = users.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            return null;
        }

        String token = tokenProvider.generateToken(username, user.getRole());
        long expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours

        return new LoginResponse(token, username, user.getRole(), expiresAt);
    }

    /**
     * LoginResponse DTO
     */
    public static class LoginResponse {
        public String token;
        public String username;
        public String role;
        public long expiresAt;

        public LoginResponse(String token, String username, String role, long expiresAt) {
            this.token = token;
            this.username = username;
            this.role = role;
            this.expiresAt = expiresAt;
        }
    }
}

