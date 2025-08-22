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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * Dull proxy controller for User entity. All business logic is implemented in workflows.
 */
@RestController
@RequestMapping("/api/users/v1")
@io.swagger.v3.oas.annotations.tags.Tag(name = "User", description = "User entity operations (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Creates a new User entity and starts the corresponding workflow. Returns technicalId only.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<?> addUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User payload",
            required = true,
            content = @Content(schema = @Schema(implementation = User.class))
        )
        @RequestBody User user
    ) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                user
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for addUser", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in addUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "Create multiple Users", description = "Creates multiple User entities. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdsResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addUsers(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of Users",
            required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = User.class)))
        )
        @RequestBody List<User> users
    ) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                users
            );
            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            for (UUID u : ids) resp.getTechnicalIds().add(u.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for addUsers", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in addUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieves the stored User entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                tid
            );
            ObjectNode entity = itemFuture.get();
            ItemResponse resp = new ItemResponse();
            resp.setTechnicalId(technicalId);
            resp.setEntity(entity);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid technicalId in getUser: {}", technicalId, iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user {}", technicalId, ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in getUser {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "List all Users", description = "Retrieves all User entities. This is a read-only operation and does not trigger workflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> getUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            ItemsResponse resp = new ItemsResponse();
            resp.setItems(array);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in getUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "Search Users by condition", description = "Searches Users using a SearchConditionRequest. Uses in-memory filtering.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemsResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Search condition",
            required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class))
        )
        @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredFuture.get();
            ItemsResponse resp = new ItemsResponse();
            resp.setItems(array);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid search request", iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in searchUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "Update User", description = "Updates an existing User entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User payload for update",
            required = true,
            content = @Content(schema = @Schema(implementation = User.class))
        )
        @RequestBody User user
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                tid,
                user
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for updateUser {}", technicalId, iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user {}", technicalId, ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    @Operation(summary = "Delete User", description = "Deletes a User entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable String technicalId
    ) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                tid
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid request for deleteUser {}", technicalId, iae);
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage(), null));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user {}", technicalId, ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Interrupted while processing request", null));
        } catch (Exception e) {
            logger.error("Unexpected error in deleteUser {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(e.getMessage(), null));
        }
    }

    private ResponseEntity<ErrorResponse> handleExecutionException(Throwable cause) {
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(cause.getMessage(), null));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument in execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage(), null));
        } else {
            logger.error("Execution error", cause);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(cause != null ? cause.getMessage() : "Execution error", null));
        }
    }

    // ======= Static DTO classes =======

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the assigned technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID assigned to the entity", example = "tech-user-0001")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technicalIds")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds = new java.util.ArrayList<>();
    }

    @Data
    @Schema(name = "ItemResponse", description = "Response for GET by technicalId")
    public static class ItemResponse {
        @Schema(description = "Technical ID of the entity", example = "tech-user-0001")
        private String technicalId;

        @Schema(description = "Stored entity as JSON")
        private ObjectNode entity;
    }

    @Data
    @Schema(name = "ItemsResponse", description = "Response containing array of entities")
    public static class ItemsResponse {
        @Schema(description = "Array of entities")
        private ArrayNode items;
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String error;
        @Schema(description = "Optional details")
        private Object details;

        public ErrorResponse() {}

        public ErrorResponse(String error, Object details) {
            this.error = error;
            this.details = details;
        }
    }
}