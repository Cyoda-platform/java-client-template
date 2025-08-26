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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/user/v1")
@Tag(name = "UserController", description = "Operations for User entity")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Adds a single user entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping
    public ResponseEntity<?> addUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AddUserRequest.class))
            )
            @RequestBody AddUserRequest request
    ) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    request.getData()
            );
            UUID id = idFuture.get();
            return ResponseEntity.ok(new IdResponse(id));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when adding user", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when adding user", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Create Users (bulk)", description = "Adds multiple user entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users created", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> addUsers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "List of user payloads",
                    required = true,
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AddUserRequest.class)))
            )
            @RequestBody AddUsersRequest request
    ) {
        try {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    request.getData()
            );
            List<UUID> ids = idsFuture.get();
            return ResponseEntity.ok(new IdsResponse(ids));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when adding users", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when adding users", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get all users", description = "Retrieves all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemsResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when getting all users", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Exception when getting all users", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Search users by simple condition", description = "Search users by a single field condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition",
                    required = true,
                    content = @Content(schema = @Schema(implementation = FilterRequest.class))
            )
            @RequestBody FilterRequest filterRequest
    ) {
        try {
            if (filterRequest.getField() == null || filterRequest.getOperator() == null) {
                throw new IllegalArgumentException("field and operator are required");
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + filterRequest.getField(), filterRequest.getOperator(), filterRequest.getValue())
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(new ItemsResponse(items));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when searching users", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when searching users", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get user by technicalId", description = "Retrieves a user by its technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @GetMapping("/{technicalId}")
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
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(new ItemResponse(item));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when getting user by id", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when getting user by id", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Update user", description = "Updates a user entity by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated user payload",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateUserRequest.class))
            )
            @RequestBody UpdateUserRequest request
    ) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id,
                    request.getData()
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when updating user", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when updating user", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Delete user", description = "Deletes a user entity by technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted", content = @Content(schema = @Schema(implementation = IdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request"),
            @ApiResponse(responseCode = "404", description = "Not found"),
            @ApiResponse(responseCode = "500", description = "Internal error")
    })
    @DeleteMapping("/{technicalId}")
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
            return ResponseEntity.ok(new IdResponse(deletedId));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException when deleting user", e);
                return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception when deleting user", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "AddUserRequest", description = "Request to add a user - contains a raw JSON object representing the entity")
    public static class AddUserRequest {
        @Schema(description = "User entity JSON payload", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "AddUsersRequest", description = "Request to add multiple users")
    public static class AddUsersRequest {
        @Schema(description = "List of user JSON payloads", required = true)
        private List<ObjectNode> data;
    }

    @Data
    @Schema(name = "UpdateUserRequest", description = "Request to update a user - contains a raw JSON object representing the entity")
    public static class UpdateUserRequest {
        @Schema(description = "Updated user entity JSON payload", required = true)
        private ObjectNode data;
    }

    @Data
    @Schema(name = "FilterRequest", description = "Simple filter request - field, operator and value")
    public static class FilterRequest {
        @Schema(description = "Field name to filter (top-level)", example = "email", required = true)
        private String field;
        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)", example = "EQUALS", required = true)
        private String operator;
        @Schema(description = "Value to compare", example = "user@example.com")
        private String value;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing a single UUID id")
    public static class IdResponse {
        @Schema(description = "Technical id")
        private UUID id;

        public IdResponse(UUID id) {
            this.id = id;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing multiple UUID ids")
    public static class IdsResponse {
        @Schema(description = "List of technical ids")
        private List<UUID> ids;

        public IdsResponse(List<UUID> ids) {
            this.ids = ids;
        }
    }

    @Data
    @Schema(name = "ItemResponse", description = "Response containing a single item JSON")
    public static class ItemResponse {
        @Schema(description = "Item JSON payload")
        private ObjectNode data;

        public ItemResponse(ObjectNode data) {
            this.data = data;
        }
    }

    @Data
    @Schema(name = "ItemsResponse", description = "Response containing multiple items")
    public static class ItemsResponse {
        @Schema(description = "Array of items")
        private ArrayNode items;

        public ItemsResponse(ArrayNode items) {
            this.items = items;
        }
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}