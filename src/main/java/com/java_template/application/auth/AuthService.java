package com.java_template.application.auth;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authentication service with hardcoded users for TMS prototype
 */
@Service
public class AuthService {
    private final Map<String, User> users = new HashMap<>();

    public AuthService() {
        // Initialize hardcoded users
        users.put("admin", new User("admin", "admin123", Set.of("ROLE_ADMIN", "ROLE_USER")));
        users.put("tester", new User("tester", "tester123", Set.of("ROLE_USER")));
    }

    /**
     * Authenticate user with username and password
     */
    public Optional<User> authenticate(String username, String password) {
        return Optional.ofNullable(users.get(username))
                .filter(user -> user.isEnabled() && user.getPassword().equals(password));
    }

    /**
     * Get user by username
     */
    public Optional<User> getUserByUsername(String username) {
        return Optional.ofNullable(users.get(username))
                .filter(User::isEnabled);
    }

    /**
     * Check if user has role
     */
    public boolean hasRole(String username, String role) {
        return getUserByUsername(username)
                .map(user -> user.getRoles().contains(role))
                .orElse(false);
    }
}

