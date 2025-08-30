package com.java_template.application.controller.user.version_1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<JsonNode>> addUser(@Valid @RequestBody User user) {
        logger.info("Adding new user: {}", user.getUsername());
        
        return entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user)
            .thenApply(technicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", technicalId.toString())
                        .put("message", "User added successfully");
                    logger.info("User added successfully with technicalId: {}", technicalId);
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                } catch (Exception e) {
                    logger.error("Error creating response for user: {}", user.getUsername(), e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error adding user: {}", user.getUsername(), throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to add user")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> getUser(@PathVariable UUID technicalId) {
        logger.info("Retrieving user with technicalId: {}", technicalId);
        
        return entityService.getItem(technicalId)
            .thenApply(dataPayload -> {
                try {
                    if (dataPayload != null && dataPayload.getData() != null) {
                        JsonNode userData = dataPayload.getData();
                        // Add technicalId to the response
                        if (userData.isObject()) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) userData)
                                .put("technicalId", technicalId.toString());
                        }
                        logger.info("User retrieved successfully: {}", technicalId);
                        return ResponseEntity.ok(userData);
                    } else {
                        logger.warn("User not found with technicalId: {}", technicalId);
                        JsonNode errorResponse = objectMapper.createObjectNode()
                            .put("error", "User not found")
                            .put("technicalId", technicalId.toString());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                    }
                } catch (Exception e) {
                    logger.error("Error processing user data for technicalId: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process user data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving user with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve user")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<JsonNode>> getAllUsers(
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(defaultValue = "1") Integer pageNumber) {
        logger.info("Retrieving all users with pageSize: {} and pageNumber: {}", pageSize, pageNumber);
        
        return entityService.getItems(User.ENTITY_NAME, User.ENTITY_VERSION, pageSize, pageNumber, null)
            .thenApply(dataPayloads -> {
                try {
                    List<JsonNode> users = dataPayloads.stream()
                        .map(DataPayload::getData)
                        .collect(Collectors.toList());
                    
                    JsonNode response = objectMapper.createObjectNode()
                        .put("count", users.size())
                        .set("users", objectMapper.valueToTree(users));
                    
                    logger.info("Retrieved {} users successfully", users.size());
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error processing users data", e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to process users data")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error retrieving users", throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to retrieve users")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @PutMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> updateUser(
            @PathVariable UUID technicalId, 
            @Valid @RequestBody User user) {
        logger.info("Updating user with technicalId: {}", technicalId);
        
        return entityService.updateItem(technicalId, user)
            .thenApply(updatedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", updatedTechnicalId.toString())
                        .put("message", "User updated successfully");
                    logger.info("User updated successfully with technicalId: {}", updatedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for user update: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error updating user with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to update user")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }

    @DeleteMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<JsonNode>> deleteUser(@PathVariable UUID technicalId) {
        logger.info("Deleting user with technicalId: {}", technicalId);
        
        return entityService.deleteItem(technicalId)
            .thenApply(deletedTechnicalId -> {
                try {
                    JsonNode response = objectMapper.createObjectNode()
                        .put("technicalId", deletedTechnicalId.toString())
                        .put("message", "User deleted successfully");
                    logger.info("User deleted successfully with technicalId: {}", deletedTechnicalId);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    logger.error("Error creating response for user deletion: {}", technicalId, e);
                    JsonNode errorResponse = objectMapper.createObjectNode()
                        .put("error", "Failed to create response")
                        .put("message", e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error deleting user with technicalId: {}", technicalId, throwable);
                JsonNode errorResponse = objectMapper.createObjectNode()
                    .put("error", "Failed to delete user")
                    .put("message", throwable.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            });
    }
}
