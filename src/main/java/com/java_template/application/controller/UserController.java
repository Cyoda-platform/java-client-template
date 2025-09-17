package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserController - Manages user account operations
 * 
 * Base Path: /api/v1/users
 * Description: REST controller for User entity CRUD operations and workflow transitions
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new user
     * POST /api/v1/users
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<User>> createUser(@RequestBody User user) {
        try {
            // Set creation timestamp
            user.setRegistrationDate(LocalDateTime.now());

            EntityWithMetadata<User> response = entityService.create(user);
            logger.info("User created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get user by technical UUID
     * GET /api/v1/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> getUserById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.getById(id, modelSpec, User.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting User by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user by username
     * GET /api/v1/users/username/{username}
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<EntityWithMetadata<User>> getUserByUsername(@PathVariable String username) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.findByBusinessId(
                    modelSpec, username, "username", User.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting User by username: {}", username, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get user by business identifier (userId)
     * GET /api/v1/users/business/{userId}
     */
    @GetMapping("/business/{userId}")
    public ResponseEntity<EntityWithMetadata<User>> getUserByBusinessId(@PathVariable String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.findByBusinessId(
                    modelSpec, userId, "userId", User.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting User by business ID: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update user with optional workflow transition
     * PUT /api/v1/users/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> updateUser(
            @PathVariable UUID id,
            @RequestBody User user,
            @RequestParam(required = false) String transitionName) {
        try {
            EntityWithMetadata<User> response = entityService.update(id, user, transitionName);
            logger.info("User updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete user by technical UUID
     * DELETE /api/v1/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("User deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all users
     * GET /api/v1/users
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<User>>> getAllUsers() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            List<EntityWithMetadata<User>> users = entityService.findAll(modelSpec, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error getting all Users", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search users by email
     * GET /api/v1/users/search?email=john@example.com
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<User>>> searchUsersByEmail(@RequestParam String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.email")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(email));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<User>> users = entityService.search(modelSpec, condition, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error searching Users by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Advanced user search
     * POST /api/v1/users/search/advanced
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<List<EntityWithMetadata<User>>> advancedUserSearch(@RequestBody UserSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getFirstName() != null && !searchRequest.getFirstName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.firstName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getFirstName())));
            }

            if (searchRequest.getLastName() != null && !searchRequest.getLastName().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.lastName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(searchRequest.getLastName())));
            }

            if (searchRequest.getEmail() != null && !searchRequest.getEmail().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getEmail())));
            }

            if (searchRequest.getIsActive() != null) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.isActive")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getIsActive())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<QueryCondition>(conditions));

            List<EntityWithMetadata<User>> users = entityService.search(modelSpec, condition, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error performing advanced user search", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * User login
     * POST /api/v1/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody LoginRequest loginRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> userWithMetadata = entityService.findByBusinessId(
                    modelSpec, loginRequest.getUsername(), "username", User.class);

            if (userWithMetadata == null) {
                return ResponseEntity.badRequest().build();
            }

            User user = userWithMetadata.entity();
            
            // Simple password check (in real app, use proper authentication)
            String encryptedPassword = "encrypted_" + loginRequest.getPassword().hashCode();
            if (!encryptedPassword.equals(user.getPassword())) {
                return ResponseEntity.badRequest().build();
            }

            // Update last login date
            user.setLastLoginDate(LocalDateTime.now());
            entityService.update(userWithMetadata.metadata().getId(), user, null);

            Map<String, Object> response = new HashMap<>();
            response.put("token", "jwt-token-" + UUID.randomUUID().toString());
            response.put("user", Map.of(
                    "userId", user.getUserId(),
                    "username", user.getUsername(),
                    "firstName", user.getFirstName(),
                    "lastName", user.getLastName()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during user login", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * User logout
     * POST /api/v1/users/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logoutUser() {
        // In real app, would invalidate JWT token
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * DTO for user search requests
     */
    @Getter
    @Setter
    public static class UserSearchRequest {
        private String firstName;
        private String lastName;
        private String email;
        private Boolean isActive;
    }

    /**
     * DTO for login requests
     */
    @Getter
    @Setter
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
