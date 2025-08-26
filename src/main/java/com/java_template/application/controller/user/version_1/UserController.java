package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/user/v1/users")
@Tag(name = "User", description = "User entity API proxy (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a User", description = "Proxy endpoint to create a single User entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Convert request DTO to entity instance (no business logic)
            User user = objectMapper.convertValue(request, User.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
            );

            UUID technicalId = idFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Users", description = "Proxy endpoint to create multiple User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BatchIdResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createUsersBatch(@RequestBody List<CreateUserRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain a non-empty list");
            }
            List<User> users = new ArrayList<>();
            for (CreateUserRequest r : requests) {
                users.add(objectMapper.convertValue(r, User.class));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    users
            );

            List<UUID> ids = idsFuture.get();
            BatchIdResponse resp = new BatchIdResponse();
            List<String> technicalIds = new ArrayList<>();
            for (UUID id : ids) technicalIds.add(id.toString());
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createUsersBatch: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating users batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createUsersBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a User by technicalId", description = "Retrieve a User entity by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }
            UserResponse resp = objectMapper.convertValue(node, UserResponse.class);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping
    public ResponseEntity<?> getUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );

            ArrayNode array = itemsFuture.get();
            List<UserResponse> respList = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    respList.add(objectMapper.convertValue(array.get(i), UserResponse.class));
                }
            }
            return ResponseEntity.ok(respList);

        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search Users by condition", description = "Retrieve Users matching a search condition (in-memory filtering)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode array = filteredFuture.get();
            List<UserResponse> respList = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    respList.add(objectMapper.convertValue(array.get(i), UserResponse.class));
                }
            }
            return ResponseEntity.ok(respList);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a User", description = "Proxy endpoint to update a User entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID id = UUID.fromString(technicalId);
            User user = objectMapper.convertValue(request, User.class);

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id,
                    user
            );

            UUID updatedId = updatedFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid input for updateUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a User", description = "Proxy endpoint to delete a User entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id
            );

            UUID deletedId = deletedFuture.get();
            IdResponse resp = new IdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deleteUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            return handleExecutionException(cause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(Throwable cause) {
        if (cause == null) {
            logger.error("ExecutionException with null cause");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown error");
        }
        logger.error("ExecutionException cause: ", cause);
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        }
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateUserRequest", description = "Request to create or update a User")
    public static class CreateUserRequest {
        @Schema(description = "Source id from provider")
        private Integer id;

        @Schema(description = "Username")
        private String username;

        @Schema(description = "Full name")
        private String name;

        @Schema(description = "Email")
        private String email;

        @Schema(description = "Phone")
        private String phone;

        @Schema(description = "Address object")
        private AddressDto address;

        @Schema(description = "Serialized raw payload")
        @JsonProperty("raw_payload")
        private String rawPayload;

        @Schema(description = "When fetched (ISO timestamp)")
        @JsonProperty("fetched_at")
        private String fetchedAt;

        @Schema(description = "Processing status")
        @JsonProperty("processing_status")
        private String processingStatus;

        @Schema(description = "Stored reference")
        @JsonProperty("stored_reference")
        private String storedReference;
    }

    @Data
    @Schema(name = "UserResponse", description = "User payload returned by the API")
    public static class UserResponse {
        @Schema(description = "Source id from provider")
        private Integer id;

        @Schema(description = "Username")
        private String username;

        @Schema(description = "Full name")
        private String name;

        @Schema(description = "Email")
        private String email;

        @Schema(description = "Phone")
        private String phone;

        @Schema(description = "Address object")
        private AddressDto address;

        @Schema(description = "Serialized raw payload")
        @JsonProperty("raw_payload")
        private String rawPayload;

        @Schema(description = "When fetched (ISO timestamp)")
        @JsonProperty("fetched_at")
        private String fetchedAt;

        @Schema(description = "Processing status")
        @JsonProperty("processing_status")
        private String processingStatus;

        @Schema(description = "Stored reference")
        @JsonProperty("stored_reference")
        private String storedReference;
    }

    @Data
    @Schema(name = "AddressDto", description = "Address object for User")
    public static class AddressDto {
        @Schema(description = "Street")
        private String street;

        @Schema(description = "City")
        private String city;

        @Schema(description = "Zipcode")
        private String zipcode;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing technicalId")
    public static class IdResponse {
        @Schema(description = "Technical UUID of the entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchIdResponse", description = "Response containing multiple technicalIds")
    public static class BatchIdResponse {
        @Schema(description = "List of technical UUIDs")
        private List<String> technicalIds;
    }
}