package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Controller", description = "API for User entity (version 1) - proxy to EntityService")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    @Operation(summary = "Create User", description = "Persist a User entity and start its workflow. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody(description = "User payload", required = true,
            content = @Content(schema = @Schema(implementation = UserRequest.class)))
                                    @org.springframework.web.bind.annotation.RequestBody UserRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            User data = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), data);
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createUser: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUser", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createUser", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Users", description = "Persist multiple User entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createUsersBatch(@RequestBody(description = "List of User payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserRequest.class))))
                                              @org.springframework.web.bind.annotation.RequestBody List<UserRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain at least one user");
            List<User> entities = requests.stream().map(this::toEntity).collect(Collectors.toList());
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), entities);
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new IdsResponse(technicalIds));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for createUsersBatch: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUsersBatch", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating users batch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in createUsersBatch", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all User entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllUsers", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllUsers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Users", description = "Search users by condition. Accepts SearchConditionRequest.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestBody(description = "Search condition request", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                         @org.springframework.web.bind.annotation.RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), condition, true);
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid searchUsers request: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchUsers", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in searchUsers", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User entity by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUserById(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                         @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), tid);
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for getUserById: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUserById", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user by id", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getUserById", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update a User entity by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                        @PathVariable String technicalId,
                                        @RequestBody(description = "User payload", required = true,
                                                content = @Content(schema = @Schema(implementation = UserRequest.class)))
                                        @org.springframework.web.bind.annotation.RequestBody UserRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID tid = UUID.fromString(technicalId);
            User data = toEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), tid, data);
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request for updateUser: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateUser", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in updateUser", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User entity by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                        @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), tid);
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId.toString()));
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for deleteUser: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteUser", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteUser", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Helper to convert request DTO to entity
    private User toEntity(UserRequest req) {
        User u = new User();
        u.setUserId(req.getUserId());
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setRole(req.getRole());
        u.setStatus(req.getStatus());
        u.setCreatedAt(req.getCreatedAt());
        return u;
    }

    // Static DTO classes for requests/responses
    @Data
    static class UserRequest {
        @Schema(description = "Business userId", example = "u123")
        private String userId;
        @Schema(description = "Full name", example = "John Doe")
        private String name;
        @Schema(description = "Email address", example = "john.doe@example.com")
        private String email;
        @Schema(description = "Role (Admin or Customer)", example = "Customer")
        private String role;
        @Schema(description = "Status (active/inactive)", example = "active")
        private String status;
        @Schema(description = "ISO timestamp of creation", example = "2023-01-01T12:00:00Z")
        private String createdAt;
    }

    @Data
    static class UserResponse {
        @Schema(description = "Business userId", example = "u123")
        private String userId;
        @Schema(description = "Full name", example = "John Doe")
        private String name;
        @Schema(description = "Email address", example = "john.doe@example.com")
        private String email;
        @Schema(description = "Role (Admin or Customer)", example = "Customer")
        private String role;
        @Schema(description = "Status (active/inactive)", example = "active")
        private String status;
        @Schema(description = "ISO timestamp of creation", example = "2023-01-01T12:00:00Z")
        private String createdAt;
    }

    @Data
    static class IdResponse {
        @Schema(description = "Technical UUID for the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    static class IdsResponse {
        @Schema(description = "List of technical UUIDs", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
        private List<String> technicalIds = new ArrayList<>();

        public IdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}