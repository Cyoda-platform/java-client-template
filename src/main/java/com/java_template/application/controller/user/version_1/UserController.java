package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

/**
 * Controller for User entity (version 1)
 *
 * Responsibilities:
 * - Proxy to EntityService
 * - Validate basic request format
 * - Map request bodies to JSON nodes and forward to EntityService
 * - Handle ExecutionException unwrapping
 *
 * Note: No business logic implemented here — controller acts as a proxy to EntityService.
 */
@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User", description = "User entity operations (v1)")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "Create a user", description = "Create a new User entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(schema = @Schema(implementation = AddResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> addUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User creation payload",
            content = @Content(schema = @Schema(implementation = UserRequest.class)))
                                         @Valid @RequestBody UserRequest request) {
        try {
            // Convert request DTO to ObjectNode (no business logic)
            ObjectNode dataNode = objectMapper.convertValue(request, ObjectNode.class);

            var idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    dataNode
            );

            UUID id = idFuture.get();
            AddResponse resp = new AddResponse(id);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addUser", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple users", description = "Create multiple User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Created",
                    content = @Content(schema = @Schema(implementation = AddMultipleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addUsers(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of user payloads",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserRequest.class))))
                                      @Valid @RequestBody List<UserRequest> requests) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (UserRequest r : requests) {
                arrayNode.add(objectMapper.convertValue(r, ObjectNode.class));
            }

            var idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    arrayNode
            );

            List<UUID> ids = idsFuture.get();
            AddMultipleResponse resp = new AddMultipleResponse(ids);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for addUsers", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while adding users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while adding users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get a user by technicalId", description = "Retrieve a User entity by technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") @NotBlank String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            var itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    uuid
            );

            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getUser: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while fetching user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all users", description = "Retrieve all User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getUsers() {
        try {
            var itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );

            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while fetching users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while fetching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search users by simple conditions", description = "Filter users by simple field based conditions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Filter with conditions",
                    content = @Content(schema = @Schema(implementation = FilterRequest.class)))
            @Valid @RequestBody FilterRequest filterRequest) {
        try {
            // Build Condition objects from DTOs
            List<Condition> conditions = new ArrayList<>();
            for (ConditionDto c : filterRequest.getConditions()) {
                // Using Condition.of with JSON path, operator and value
                conditions.add(Condition.of(c.getJsonPath(), c.getOperator(), c.getValue()));
            }

            // Combine conditions into a group (AND)
            SearchConditionRequest conditionGroup = SearchConditionRequest.group("AND",
                    conditions.toArray(new Condition[0]));

            var filteredFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    conditionGroup,
                    true
            );

            ArrayNode result = filteredFuture.get();
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while searching users", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while searching users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update a user", description = "Update a User entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated",
                    content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") @NotBlank String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User update payload",
                    content = @Content(schema = @Schema(implementation = UserRequest.class)))
            @Valid @RequestBody UserRequest request) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode dataNode = objectMapper.convertValue(request, ObjectNode.class);

            var updatedFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    uuid,
                    dataNode
            );

            UUID updatedId = updatedFuture.get();
            UpdateResponse resp = new UpdateResponse(updatedId);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateUser: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while updating user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while updating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a user", description = "Delete a User entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted",
                    content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") @NotBlank String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);

            var deletedFuture = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    uuid
            );

            UUID deletedId = deletedFuture.get();
            DeleteResponse resp = new DeleteResponse(deletedId);
            return ResponseEntity.ok(resp);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteUser: {}", technicalId, iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while deleting user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error while deleting user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to unwrap ExecutionException and map cause to appropriate response
    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found", cause);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Illegal argument in entity service call", cause);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("ExecutionException in entity service call", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        }
    }

    // -----------------------------
    // Static DTO classes
    // -----------------------------

    @Data
    @Schema(name = "UserRequest", description = "User create/update request")
    public static class UserRequest {
        // Using a few common fields; mapping is generic, entities are forwarded as JSON.
        @Schema(description = "User first name", example = "John")
        private String firstName;

        @Schema(description = "User last name", example = "Doe")
        private String lastName;

        @Schema(description = "User email", example = "john.doe@example.com")
        private String email;

        @Schema(description = "Additional arbitrary data", example = "{}")
        private Object additionalData;
    }

    @Data
    @Schema(name = "AddResponse", description = "Response with created id")
    public static class AddResponse {
        @Schema(description = "Technical id of created entity")
        private UUID id;

        public AddResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "AddMultipleResponse", description = "Response with created ids")
    public static class AddMultipleResponse {
        @Schema(description = "List of created technical ids")
        private List<UUID> ids;

        public AddMultipleResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response with updated id")
    public static class UpdateResponse {
        @Schema(description = "Technical id of updated entity")
        private UUID id;

        public UpdateResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response with deleted id")
    public static class DeleteResponse {
        @Schema(description = "Technical id of deleted entity")
        private UUID id;

        public DeleteResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "FilterRequest", description = "Request to filter items by simple conditions")
    public static class FilterRequest {
        @Schema(description = "List of conditions to apply (combined with AND)")
        private List<ConditionDto> conditions;
    }

    @Data
    @Schema(name = "ConditionDto", description = "Single simple condition")
    public static class ConditionDto {
        @Schema(description = "JSON path to the field (e.g. $.email)", example = "$.email")
        private String jsonPath;

        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, ...)", example = "EQUALS")
        private String operator;

        @Schema(description = "Value to compare", example = "john.doe@example.com")
        private String value;
    }

}