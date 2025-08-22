package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for User entity (version 1).
 *
 * Responsibilities:
 * - Accept HTTP requests
 * - Validate basic request format
 * - Proxy calls to EntityService (no business logic)
 * - Return appropriate HTTP responses
 * - Handle ExecutionException unwrapping per requirements
 */
@RestController
@RequestMapping("/api/user/v1")
@Tag(name = "UserController", description = "Operations for User entity (v1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a user", description = "Creates a new User and returns its technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            ObjectNode data = mapper.createObjectNode();
            if (request.getTechnicalId() != null) data.put("technicalId", request.getTechnicalId());
            if (request.getName() != null) data.put("name", request.getName());
            if (request.getEmail() != null) data.put("email", request.getEmail());
            if (request.getVerificationStatus() != null) data.put("verificationStatus", request.getVerificationStatus());
            if (request.getCreatedAt() != null) data.put("createdAt", request.getCreatedAt());
            if (request.getUpdatedAt() != null) data.put("updatedAt", request.getUpdatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                data
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for addUser", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while adding user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Create multiple users", description = "Creates multiple Users and returns their technical ids.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addUsers(@Valid @RequestBody List<CreateUserRequest> requests) {
        try {
            ArrayNode array = mapper.createArrayNode();
            for (CreateUserRequest request : requests) {
                ObjectNode data = mapper.createObjectNode();
                if (request.getTechnicalId() != null) data.put("technicalId", request.getTechnicalId());
                if (request.getName() != null) data.put("name", request.getName());
                if (request.getEmail() != null) data.put("email", request.getEmail());
                if (request.getVerificationStatus() != null) data.put("verificationStatus", request.getVerificationStatus());
                if (request.getCreatedAt() != null) data.put("createdAt", request.getCreatedAt());
                if (request.getUpdatedAt() != null) data.put("updatedAt", request.getUpdatedAt());
                array.add(data);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                array
            );

            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid request for addUsers", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while adding users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while adding users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get user by technicalId", description = "Retrieves a User by its technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode item = itemFuture.get();
            UserResponse resp = new UserResponse(item);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId for getUser: {}", technicalId, ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while getting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all users", description = "Retrieves all Users.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            List<UserResponse> respList = new ArrayList<>();
            for (JsonNode node : items) {
                respList.add(new UserResponse(node));
            }
            return ResponseEntity.ok(respList);
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while getting users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Search users by condition", description = "Retrieves Users by a search condition.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(@Valid @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = filteredItemsFuture.get();
            List<UserResponse> respList = new ArrayList<>();
            for (JsonNode node : items) {
                respList.add(new UserResponse(node));
            }
            return ResponseEntity.ok(respList);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid search condition", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while searching users", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Update a user", description = "Updates an existing User.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        try {
            ObjectNode data = mapper.createObjectNode();
            if (request.getTechnicalId() != null) data.put("technicalId", request.getTechnicalId());
            if (request.getName() != null) data.put("name", request.getName());
            if (request.getEmail() != null) data.put("email", request.getEmail());
            if (request.getVerificationStatus() != null) data.put("verificationStatus", request.getVerificationStatus());
            if (request.getCreatedAt() != null) data.put("createdAt", request.getCreatedAt());
            if (request.getUpdatedAt() != null) data.put("updatedAt", request.getUpdatedAt());

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(technicalId),
                data
            );

            UUID id = updatedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid update request", ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while updating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Delete a user", description = "Deletes a User by technical id.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            UUID id = deletedId.get();
            return ResponseEntity.ok(new TechnicalIdResponse(id));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid technicalId for deleteUser: {}", technicalId, ex);
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (ExecutionException ex) {
            return handleExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error while deleting user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Illegal argument in async execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(cause.getMessage());
        } else {
            logger.error("Execution exception", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ex.getMessage());
        }
    }

    // Static DTO classes

    public record TechnicalIdResponse(@Schema(description = "Technical id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID id) {
    }

    public static class IdsResponse {
        @Schema(description = "List of technical ids")
        private List<UUID> ids;

        public IdsResponse() {
        }

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }

        public List<UUID> getIds() {
            return ids;
        }

        public void setIds(List<UUID> ids) {
            this.ids = ids;
        }
    }

    public static class UserResponse {
        @Schema(description = "User raw data as JSON")
        private JsonNode data;

        public UserResponse() {
        }

        public UserResponse(JsonNode data) {
            this.data = data;
        }

        public JsonNode getData() {
            return data;
        }

        public void setData(JsonNode data) {
            this.data = data;
        }
    }

    public static class CreateUserRequest {
        @Schema(description = "Technical id (optional). If provided, will be stored as-is", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Name of the user", example = "John Doe")
        private String name;

        @Schema(description = "Email of the user", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Verification status", example = "VERIFIED")
        private String verificationStatus;

        @Schema(description = "Created timestamp", example = "2025-08-22T12:00:00Z")
        private String createdAt;

        @Schema(description = "Updated timestamp", example = "2025-08-22T12:00:00Z")
        private String updatedAt;

        public CreateUserRequest() {
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getVerificationStatus() {
            return verificationStatus;
        }

        public void setVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class UpdateUserRequest {
        @Schema(description = "Technical id (optional)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Name of the user", example = "John Doe")
        private String name;

        @Schema(description = "Email of the user", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Verification status", example = "VERIFIED")
        private String verificationStatus;

        @Schema(description = "Created timestamp", example = "2025-08-22T12:00:00Z")
        private String createdAt;

        @Schema(description = "Updated timestamp", example = "2025-08-22T12:00:00Z")
        private String updatedAt;

        public UpdateUserRequest() {
        }

        public String getTechnicalId() {
            return technicalId;
        }

        public void setTechnicalId(String technicalId) {
            this.technicalId = technicalId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getVerificationStatus() {
            return verificationStatus;
        }

        public void setVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}