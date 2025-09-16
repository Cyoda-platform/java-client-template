package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UserController - Manage user accounts
 * 
 * Base Path: /api/users
 * Entity: User
 * Purpose: Manage user accounts
 */
@RestController
@RequestMapping("/api/users")
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
     * Get all users (admin only) with optional filtering
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<User>>> getAllUsers(
            @RequestParam(required = false) String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            List<EntityWithMetadata<User>> users = entityService.findAll(modelSpec, User.class);
            
            // Filter by status if provided (status is in metadata)
            if (status != null) {
                users = users.stream()
                        .filter(user -> status.equals(user.metadata().getState()))
                        .collect(Collectors.toList());
            }

            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error getting users", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get user by technical UUID
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> getUserById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.getById(id, modelSpec, User.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting user by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user by business identifier
     * GET /api/users/business/{userId}
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
            logger.error("Error getting user by business ID: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Create new user
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<User>> createUser(@RequestBody User user) {
        try {
            // Set creation timestamp
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<User> response = entityService.create(user);
            logger.info("User created with ID: {}", response.metadata().getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating user", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update user with optional workflow transition
     * PUT /api/users/{id}?transitionName=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> updateUser(
            @PathVariable UUID id,
            @RequestBody User user,
            @RequestParam(required = false) String transitionName) {
        try {
            // Set update timestamp
            user.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<User> response = entityService.update(id, user, transitionName);
            logger.info("User updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating user", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Suspend user account
     * PUT /api/users/{userId}/suspend
     */
    @PutMapping("/{userId}/suspend")
    public ResponseEntity<EntityWithMetadata<User>> suspendUser(
            @PathVariable String userId,
            @RequestBody(required = false) SuspensionRequest request) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(
                    modelSpec, userId, "userId", User.class);

            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<User> response = entityService.update(
                    userEntity.metadata().getId(), userEntity.entity(), "suspend_user");
            logger.info("User {} suspended", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error suspending user: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove suspension
     * PUT /api/users/{userId}/unsuspend
     */
    @PutMapping("/{userId}/unsuspend")
    public ResponseEntity<EntityWithMetadata<User>> unsuspendUser(@PathVariable String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(
                    modelSpec, userId, "userId", User.class);

            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<User> response = entityService.update(
                    userEntity.metadata().getId(), userEntity.entity(), "unsuspend_user");
            logger.info("User {} unsuspended", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error unsuspending user: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Deactivate user account
     * PUT /api/users/{userId}/deactivate
     */
    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<EntityWithMetadata<User>> deactivateUser(@PathVariable String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(
                    modelSpec, userId, "userId", User.class);

            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<User> response = entityService.update(
                    userEntity.metadata().getId(), userEntity.entity(), "deactivate_user");
            logger.info("User {} deactivated", userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deactivating user: {}", userId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete user
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("User deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting user", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // Request DTOs

    /**
     * DTO for suspension requests
     */
    @Getter
    @Setter
    public static class SuspensionRequest {
        private String reason;
        private String suspensionDuration;
    }
}
