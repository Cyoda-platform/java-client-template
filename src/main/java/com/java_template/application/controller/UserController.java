package com.java_template.application.controller;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private EntityService entityService;

    /**
     * Create a new user
     * POST /users
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createUser(@RequestBody CreateUserRequest request) {
        try {
            // Create new User entity
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());

            // Validate the user
            if (!user.isValid()) {
                return ResponseEntity.badRequest().build();
            }

            // Save the user using EntityService
            CompletableFuture<UUID> future = entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user);
            UUID entityId = future.get(); // In production, handle this asynchronously

            // Return the technical ID
            return ResponseEntity.ok(Map.of("technicalId", entityId.toString()));
        } catch (Exception e) {
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
            // TODO: In a real implementation, you would use entityService.getItem() and deserialize
            // For now, return a placeholder user to match the API specification
            User user = new User();
            user.setId(technicalId);
            user.setName("Sample User");
            user.setEmail("sample@example.com");
            user.setPhone("1234567890");

            return ResponseEntity.ok(user);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Request DTO for creating users
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
    }
}
