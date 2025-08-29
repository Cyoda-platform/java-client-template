package com.java_template.application.controller;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new user
     * POST /users
     * Request: {"name": "John Doe", "email": "john@example.com", "phone": "1234567890"}
     * Response: {"technicalId": "user5678"}
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createUser(@RequestBody CreateUserRequest request) {
        try {
            log.info("Creating new user: {}", request);
            
            // Create User entity
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            
            // Validate entity
            if (!user.isValid()) {
                log.error("Invalid user data: {}", user);
                return ResponseEntity.badRequest().build();
            }
            
            // Save to Cyoda
            UUID entityId = entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user).get();
            user.setId(entityId.toString());

            log.info("User created successfully with ID: {}", user.getId());
            return ResponseEntity.ok(Map.of("technicalId", user.getId()));
            
        } catch (Exception e) {
            log.error("Error creating user", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieve user details by technicalId
     * GET /users/{technicalId}
     */
    @GetMapping("/{technicalId}")
    public ResponseEntity<User> getUser(@PathVariable String technicalId) {
        try {
            log.info("Retrieving user with ID: {}", technicalId);

            // For now, return a simple response - full implementation would require
            // proper entity retrieval and deserialization from DataPayload
            // This is a simplified version for compilation
            log.warn("User retrieval not fully implemented yet");
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("Error retrieving user with ID: " + technicalId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Inner class for request body
    public static class CreateUserRequest {
        private String name;
        private String email;
        private String phone;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        @Override
        public String toString() {
            return "CreateUserRequest{" +
                    "name='" + name + '\'' +
                    ", email='" + email + '\'' +
                    ", phone='" + phone + '\'' +
                    '}';
        }
    }
}
