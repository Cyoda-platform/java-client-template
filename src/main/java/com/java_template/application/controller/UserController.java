package com.java_template.application.controller;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.java_template.common.config.Config;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private EntityService entityService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Create a new user.
     * Request:
     * {
     *     "name": "John Doe",
     *     "email": "john@example.com",
     *     "phone": "1234567890"
     * }
     * Response:
     * {
     *     "technicalId": "user5678"
     * }
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<ObjectNode>> createUser(@RequestBody User user) {
        try {
            // Generate ID for the user
            user.setId(UUID.randomUUID().toString());

            // Validate the user
            if (!user.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid user data. All fields (name, email, phone) are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user)
                .thenApply(technicalId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", technicalId.toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to create user: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (Exception e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid request: " + e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Retrieve user details by technicalId.
     */
    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getUser(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.getItem(uuid)
                .thenApply(userData -> {
                    if (userData == null || userData.getData() == null) {
                        ObjectNode errorResponse = objectMapper.createObjectNode();
                        errorResponse.put("error", "User not found");
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body((JsonNode) errorResponse);
                    }
                    return ResponseEntity.ok(userData.getData());
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to retrieve user: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Get all users.
     */
    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllUsers() {
        return entityService.getItems(User.ENTITY_NAME, User.ENTITY_VERSION, null, null, null)
            .thenApply(users -> {
                ObjectNode response = objectMapper.createObjectNode();
                response.set("users", objectMapper.valueToTree(users));
                return ResponseEntity.ok((JsonNode) response);
            })
            .exceptionally(throwable -> {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Failed to retrieve users: " + throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((JsonNode) errorResponse);
            });
    }

    /**
     * Update a user by technicalId.
     */
    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> updateUser(
            @PathVariable String technicalId, 
            @RequestBody User user) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            // Set the ID to match the path parameter
            user.setId(technicalId);

            // Validate the user
            if (!user.isValid()) {
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("error", "Invalid user data. All fields (name, email, phone) are required.");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(errorResponse)
                );
            }

            return entityService.updateItem(uuid, user)
                .thenApply(updatedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", updatedId.toString());
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to update user: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }

    /**
     * Delete a user by technicalId.
     */
    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> deleteUser(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            
            return entityService.deleteItem(uuid)
                .thenApply(deletedId -> {
                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("technicalId", deletedId.toString());
                    response.put("message", "User deleted successfully");
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", "Failed to delete user: " + throwable.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                });

        } catch (IllegalArgumentException e) {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", "Invalid technicalId format");
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(errorResponse)
            );
        }
    }
}
