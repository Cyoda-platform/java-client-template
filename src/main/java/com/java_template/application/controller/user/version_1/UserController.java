package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.user.version_1.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for User entity - all business rules implemented in workflows.
 */
@Tag(name = "User Controller", description = "Proxy endpoints for User entity (version 1)")
@RestController
@RequestMapping("/api/user/v1")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Create a User entity (proxy to EntityService). No business logic in controller.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users")
    public ResponseEntity<?> createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User payload", required = true,
            content = @Content(schema = @Schema(implementation = UserRequest.class)))
        @RequestBody UserRequest request
    ) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                request
            );
            UUID id = idFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create user", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while creating user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple Users", description = "Bulk create Users (proxy to EntityService).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = IdResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users/bulk")
    public ResponseEntity<?> createUsersBulk(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of User payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserRequest.class))))
        @RequestBody List<UserRequest> requests
    ) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                requests
            );
            List<UUID> ids = idsFuture.get();
            BulkIdResponse resp = new BulkIdResponse();
            resp.setTechnicalIds(ids.stream().map(UUID::toString).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid bulk create request for users", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while bulk creating users", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while bulk creating users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while bulk creating users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User by technicalId (proxy to EntityService).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/users/{technicalId}")
    public ResponseEntity<?> getUserById(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for getUserById: {}", technicalId, ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all Users (proxy to EntityService).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while retrieving all users", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving all users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while retrieving all users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search Users by condition", description = "Search Users by simple conditions (proxy to EntityService). Use SearchConditionRequest.group with Condition.of for basic queries.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users/search")
    public ResponseEntity<?> searchUsersByCondition(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "SearchConditionRequest", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
        @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid search condition for users", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while searching users", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update a User by technicalId (proxy to EntityService).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/users/{technicalId}")
    public ResponseEntity<?> updateUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User payload", required = true,
            content = @Content(schema = @Schema(implementation = UserRequest.class)))
        @RequestBody UserRequest request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                id,
                request
            );
            UUID updatedId = updatedIdFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to update user {}", technicalId, ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while updating user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User by technicalId (proxy to EntityService).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/users/{technicalId}")
    public ResponseEntity<?> deleteUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                id
            );
            UUID deletedId = deletedIdFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid technicalId for deleteUser: {}", technicalId, ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("Execution error while deleting user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Static DTO classes for request/response payloads

    @Data
    @Schema(name = "UserRequest", description = "User request payload")
    public static class UserRequest {
        @Schema(description = "Domain id", example = "user-123")
        private String id;

        @Schema(description = "Full name", example = "Jane Doe")
        private String name;

        @Schema(description = "Contact email", example = "jane@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+10000000000")
        private String phone;

        @Schema(description = "Role (customer/admin/shelter_staff)", example = "customer")
        private String role;

        @Schema(description = "Postal address", example = "123 Main St")
        private String address;

        @Schema(description = "Favorite pet ids", example = "[\"pet-1\",\"pet-2\"]")
        private List<String> favorites;

        @Schema(description = "Status (created/validation/verification/active/suspended/archived)", example = "active")
        private String status;

        @Schema(description = "Created timestamp ISO", example = "2025-06-01T09:00:00Z")
        private String createdAt;

        @Schema(description = "Updated timestamp ISO", example = "2025-07-01T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "UserResponse", description = "User response payload")
    public static class UserResponse {
        @Schema(description = "Domain id", example = "user-123")
        private String id;

        @Schema(description = "Full name", example = "Jane Doe")
        private String name;

        @Schema(description = "Contact email", example = "jane@example.com")
        private String email;

        @Schema(description = "Contact phone", example = "+10000000000")
        private String phone;

        @Schema(description = "Role (customer/admin/shelter_staff)", example = "customer")
        private String role;

        @Schema(description = "Postal address", example = "123 Main St")
        private String address;

        @Schema(description = "Favorite pet ids", example = "[\"pet-1\",\"pet-2\"]")
        private List<String> favorites;

        @Schema(description = "Status (created/validation/verification/active/suspended/archived)", example = "active")
        private String status;

        @Schema(description = "Created timestamp ISO", example = "2025-06-01T09:00:00Z")
        private String createdAt;

        @Schema(description = "Updated timestamp ISO", example = "2025-07-01T12:00:00Z")
        private String updatedAt;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing technical id")
    public static class IdResponse {
        @Schema(description = "Technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkIdResponse", description = "Bulk create response with list of technical ids")
    public static class BulkIdResponse {
        @Schema(description = "List of technical ids", example = "[\"id1\",\"id2\"]")
        private List<String> technicalIds;
    }
}