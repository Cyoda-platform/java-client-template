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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "Operations for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Persist a new User entity. Returns only technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload", required = true,
            content = @Content(schema = @Schema(implementation = UserRequest.class)))
                                    @RequestBody UserRequest request) {
        try {
            // Reuse entity class: map request to User entity (controller only proxies; no business logic)
            User user = new User();
            user.setId(request.getId());
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setIdentificationStatus(request.getIdentificationStatus());
            user.setCreatedAt(request.getCreatedAt());
            user.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when creating user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Users", description = "Persist multiple User entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createUsersBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Users creation payload", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserRequest.class))))
                                           @RequestBody List<UserRequest> requests) {
        try {
            List<User> users = requests.stream().map(r -> {
                User u = new User();
                u.setId(r.getId());
                u.setEmail(r.getEmail());
                u.setName(r.getName());
                u.setIdentificationStatus(r.getIdentificationStatus());
                u.setCreatedAt(r.getCreatedAt());
                u.setUpdatedAt(r.getUpdatedAt());
                return u;
            }).toList();

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    users
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).toList();
            return ResponseEntity.ok(new TechnicalIdsResponse(technicalIds));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when creating users bulk", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating users bulk", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when creating users bulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a persisted User by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode entity = itemFuture.get();
            return ResponseEntity.ok(new ItemResponse(technicalId, entity));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when getting user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when getting user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all persisted Users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when getting all users", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when getting all users", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when getting all users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Users by condition", description = "Retrieve Users by simple search condition (in-memory).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest conditionRequest) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    conditionRequest,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when searching users", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when searching users", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when searching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User entity by technicalId. Returns updated technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload", required = true,
                    content = @Content(schema = @Schema(implementation = UserRequest.class)))
            @RequestBody UserRequest request) {
        try {
            User user = new User();
            user.setId(request.getId());
            user.setEmail(request.getEmail());
            user.setName(request.getName());
            user.setIdentificationStatus(request.getIdentificationStatus());
            user.setCreatedAt(request.getCreatedAt());
            user.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    user
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when updating user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when updating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User by technicalId. Returns deleted technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when deleting user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (Exception e) {
            logger.error("Unexpected error when deleting user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Static DTOs for request/response payloads

    @Data
    @Schema(name = "UserRequest", description = "Payload to create or update a User")
    public static class UserRequest {
        @Schema(description = "Business id of the user", example = "user-1")
        private String id;

        @Schema(description = "Email address", example = "a@x.com", nullable = true)
        private String email;

        @Schema(description = "Display name", example = "Alice", nullable = true)
        private String name;

        @Schema(description = "Identification status (ANON/IDENTIFIED)", example = "ANON")
        private String identificationStatus;

        @Schema(description = "Created at timestamp (optional)", example = "2025-01-01T00:00:00Z", nullable = true)
        private String createdAt;

        @Schema(description = "Updated at timestamp (optional)", example = "2025-01-01T00:00:00Z", nullable = true)
        private String updatedAt;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier", example = "tx-user-001")
        private String technicalId;

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical identifiers")
        private List<String> technicalIds;

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "ItemResponse", description = "Response containing technicalId and entity payload")
    public static class ItemResponse {
        @Schema(description = "Technical identifier", example = "tx-user-001")
        private String technicalId;

        @Schema(description = "Entity payload")
        private ObjectNode entity;

        public ItemResponse(String technicalId, ObjectNode entity) {
            this.technicalId = technicalId;
            this.entity = entity;
        }
    }
}