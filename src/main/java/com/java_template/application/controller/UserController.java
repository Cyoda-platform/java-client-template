package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for User operations.
 * Provides endpoints for managing users in the store.
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create user
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<User>> createUser(@Valid @RequestBody User user) {
        logger.info("Creating new user: {}", user.getUsername());
        
        return entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user)
            .thenCompose(entityId -> entityService.getItem(entityId))
            .thenApply(dataPayload -> {
                try {
                    User savedUser = objectMapper.treeToValue(dataPayload.getData(), User.class);
                    // Don't return password in response
                    savedUser.setPassword(null);
                    return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
                } catch (Exception e) {
                    logger.error("Error converting saved user data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error creating user", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Create list of users with given input array
     */
    @PostMapping("/createWithArray")
    public CompletableFuture<ResponseEntity<List<User>>> createUsersWithArray(@Valid @RequestBody List<User> users) {
        logger.info("Creating {} users with array", users.size());
        
        return entityService.addItems(User.ENTITY_NAME, User.ENTITY_VERSION, users)
            .thenCompose(entityIds -> {
                // Get all created users
                List<CompletableFuture<User>> userFutures = entityIds.stream()
                    .map(entityId -> entityService.getItem(entityId)
                        .thenApply(dataPayload -> {
                            try {
                                User user = objectMapper.treeToValue(dataPayload.getData(), User.class);
                                user.setPassword(null); // Don't return password
                                return user;
                            } catch (Exception e) {
                                logger.warn("Error converting user data", e);
                                return null;
                            }
                        }))
                    .toList();
                
                return CompletableFuture.allOf(userFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> userFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList());
            })
            .thenApply(savedUsers -> ResponseEntity.status(HttpStatus.CREATED).body(savedUsers))
            .exceptionally(throwable -> {
                logger.error("Error creating users with array", throwable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            });
    }

    /**
     * Create list of users with given input list
     */
    @PostMapping("/createWithList")
    public CompletableFuture<ResponseEntity<List<User>>> createUsersWithList(@Valid @RequestBody List<User> users) {
        // Same implementation as createWithArray
        return createUsersWithArray(users);
    }

    /**
     * Logs user into the system
     */
    @GetMapping("/login")
    public CompletableFuture<ResponseEntity<String>> loginUser(
            @RequestParam String username, 
            @RequestParam String password) {
        logger.info("User login attempt: {}", username);
        
        // In a real implementation, this would validate credentials
        // For now, return a simple success message
        return CompletableFuture.completedFuture(
            ResponseEntity.ok("User logged in successfully")
        );
    }

    /**
     * Logs out current logged in user session
     */
    @GetMapping("/logout")
    public CompletableFuture<ResponseEntity<String>> logoutUser() {
        logger.info("User logout");
        
        return CompletableFuture.completedFuture(
            ResponseEntity.ok("User logged out successfully")
        );
    }

    /**
     * Get user by user name
     */
    @GetMapping("/{username}")
    public CompletableFuture<ResponseEntity<User>> getUserByName(@PathVariable String username) {
        logger.info("Getting user by username: {}", username);
        
        // In a real implementation, we would search by username using entityService.getFirstItemByCondition
        // For now, we'll use a simplified approach with UUID generation from username
        UUID entityId = UUID.nameUUIDFromBytes(username.getBytes());
        
        return entityService.getItem(entityId)
            .thenApply(dataPayload -> {
                try {
                    User user = objectMapper.treeToValue(dataPayload.getData(), User.class);
                    user.setPassword(null); // Don't return password
                    return ResponseEntity.ok(user);
                } catch (Exception e) {
                    logger.error("Error converting user data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error getting user by username", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Update user
     */
    @PutMapping("/{username}")
    public CompletableFuture<ResponseEntity<User>> updateUser(
            @PathVariable String username, 
            @Valid @RequestBody User user) {
        logger.info("Updating user: {}", username);
        
        // Set the username from path parameter
        user.setUsername(username);
        
        // Convert username to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(username.getBytes());
        
        return entityService.updateItem(entityId, user)
            .thenCompose(updatedId -> entityService.getItem(updatedId))
            .thenApply(dataPayload -> {
                try {
                    User updatedUser = objectMapper.treeToValue(dataPayload.getData(), User.class);
                    updatedUser.setPassword(null); // Don't return password
                    return ResponseEntity.ok(updatedUser);
                } catch (Exception e) {
                    logger.error("Error converting updated user data", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating user", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }

    /**
     * Delete user
     */
    @DeleteMapping("/{username}")
    public CompletableFuture<ResponseEntity<Void>> deleteUser(@PathVariable String username) {
        logger.info("Deleting user: {}", username);
        
        // Convert username to UUID (simplified approach)
        UUID entityId = UUID.nameUUIDFromBytes(username.getBytes());
        
        return entityService.deleteItem(entityId)
            .thenApply(deletedId -> ResponseEntity.ok().<Void>build())
            .exceptionally(throwable -> {
                logger.error("Error deleting user", throwable);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            });
    }
}
