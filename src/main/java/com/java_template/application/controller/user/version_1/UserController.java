package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping(path = "/user/v1", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User", description = "User entity API")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create user", description = "Add a single user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addUser(@RequestBody UserRequest request) {
        try {
            User user = mapToEntity(request);
            ObjectNode data = objectMapper.valueToTree(user);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    data
            );

            UUID id = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for addUser", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addUser", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding user", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in addUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create users (bulk)", description = "Add multiple users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addUsersBulk(@RequestBody BulkUserRequest request) {
        try {
            List<User> users = new ArrayList<>();
            if (request.getUsers() != null) {
                for (UserRequest r : request.getUsers()) {
                    users.add(mapToEntity(r));
                }
            }

            ArrayNode data = objectMapper.valueToTree(users);

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    data
            );

            List<UUID> ids = idsFuture.get();

            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            resp.setTechnicalIds(new ArrayList<>());
            for (UUID id : ids) resp.getTechnicalIds().add(id.toString());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid bulk request for addUsersBulk", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in addUsersBulk", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding users bulk", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in addUsersBulk", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get user", description = "Retrieve a user by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> getUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode data = itemFuture.get();
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getUser={}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUser", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all users", description = "Retrieve all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping(path = "", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> getUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );

            ArrayNode data = itemsFuture.get();
            return ResponseEntity.ok(data);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUsers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting users", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in getUsers", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search users", description = "Search users by condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchUsers(@RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode data = itemsFuture.get();
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchUsers", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in searchUsers", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update user", description = "Update a user by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody UserRequest request) {
        try {
            User user = mapToEntity(request);
            ObjectNode data = objectMapper.valueToTree(user);

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    data
            );

            UUID id = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateUser", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateUser", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in updateUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete user", description = "Delete a user by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping(path = "/{technicalId}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            UUID id = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteUser={}", technicalId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteUser", ee);
                return ResponseEntity.status(500).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", ie);
            return ResponseEntity.status(500).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error in deleteUser", ex);
            return ResponseEntity.status(500).body(ex.getMessage());
        }
    }

    private User mapToEntity(UserRequest request) {
        User user = new User();
        user.setId(request.getId());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setIdentificationStatus(request.getIdentificationStatus());
        user.setCreatedAt(request.getCreatedAt());
        user.setUpdatedAt(request.getUpdatedAt());
        return user;
    }

    @Data
    public static class UserRequest {
        @Schema(description = "Technical id of the user", required = false, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String id;

        @Schema(description = "Email of the user", required = true, example = "user@example.com")
        private String email;

        @Schema(description = "Name of the user", required = false, example = "John Doe")
        private String name;

        @Schema(description = "Identification status", required = false, example = "IDENTIFIED")
        private String identificationStatus;

        @Schema(description = "Created at timestamp", required = false, example = "2025-08-22T15:00:00Z")
        private String createdAt;

        @Schema(description = "Updated at timestamp", required = false, example = "2025-08-22T15:10:00Z")
        private String updatedAt;
    }

    @Data
    public static class BulkUserRequest {
        @Schema(description = "List of users to create", required = true)
        private List<UserRequest> users;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Created/affected technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "Created technical ids")
        private List<String> technicalIds;
    }
}