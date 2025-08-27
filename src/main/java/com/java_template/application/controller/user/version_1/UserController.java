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
import java.util.ArrayList;
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

@RestController
@RequestMapping("/api/user/v1")
@Tag(name = "User Controller", description = "CRUD proxy operations for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Persist a single User entity (proxy to EntityService.addItem)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User payload",
            content = @Content(schema = @Schema(implementation = CreateUserRequest.class)))
                                            @RequestBody CreateUserRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            User user = toUser(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                user
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new IdResponse(technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on createUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create multiple Users", description = "Persist multiple User entities (proxy to EntityService.addItems)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdsResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users/batch")
    public ResponseEntity<?> createUsers(@io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "List of users",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateUserRequest.class))))
                                              @RequestBody List<CreateUserRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request list is required");
            List<User> users = new ArrayList<>();
            for (CreateUserRequest r : requests) users.add(toUser(r));
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                users
            );
            List<UUID> ids = idsFuture.get();
            List<String> stringIds = new ArrayList<>();
            for (UUID id : ids) stringIds.add(id.toString());
            return ResponseEntity.ok(new IdsResponse(stringIds));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on createUsers: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createUsers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in createUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User by its technical UUID (proxy to EntityService.getItem)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = User.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/users/{technicalId}")
    public ResponseEntity<?> getUserByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on getUserByTechnicalId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUserByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getUserByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getUserByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all User entities (proxy to EntityService.getItems)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = User.class)))),
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
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllUsers", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get Users by condition", description = "Retrieve users filtered by a simple search condition (proxy to EntityService.getItemsByCondition)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = User.class)))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/users/search")
    public ResponseEntity<?> getUsersByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on getUsersByCondition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getUsersByCondition", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in getUsersByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getUsersByCondition", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update a User entity by technicalId (proxy to EntityService.updateItem)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/users/{technicalId}")
    public ResponseEntity<?> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User payload", content = @Content(schema = @Schema(implementation = UpdateUserRequest.class)))
            @RequestBody UpdateUserRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID uuid = UUID.fromString(technicalId);
            User user = toUser(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid,
                user
            );
            UUID updatedId = updatedFuture.get();
            return ResponseEntity.ok(new IdResponse(updatedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on updateUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User entity by technicalId (proxy to EntityService.deleteItem)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = IdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/users/{technicalId}")
    public ResponseEntity<?> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid
            );
            UUID deletedId = deletedFuture.get();
            return ResponseEntity.ok(new IdResponse(deletedId.toString()));
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request on deleteUser: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted in deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // Helper to map CreateUserRequest/UpdateUserRequest to User entity
    private User toUser(CreateUserRequest req) {
        User u = new User();
        u.setId(req.getId());
        u.setEmail(req.getEmail());
        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setAvatar(req.getAvatar());
        u.setRetrievedAt(req.getRetrievedAt());
        u.setSource(req.getSource());
        return u;
    }

    private User toUser(UpdateUserRequest req) {
        User u = new User();
        u.setId(req.getId());
        u.setEmail(req.getEmail());
        u.setFirstName(req.getFirstName());
        u.setLastName(req.getLastName());
        u.setAvatar(req.getAvatar());
        u.setRetrievedAt(req.getRetrievedAt());
        u.setSource(req.getSource());
        return u;
    }

    // Static DTOs

    @Data
    @Schema(name = "CreateUserRequest", description = "Payload to create a User")
    public static class CreateUserRequest {
        @Schema(description = "Remote/business id", example = "2")
        private Integer id;
        @Schema(description = "Avatar URL", example = "https://reqres.in/img/faces/2-image.jpg")
        private String avatar;
        @Schema(description = "Email", example = "janet.weaver@reqres.in")
        private String email;
        @Schema(description = "First name", example = "Janet")
        private String firstName;
        @Schema(description = "Last name", example = "Weaver")
        private String lastName;
        @Schema(description = "ISO timestamp when retrieved", example = "2025-08-27T10:00:02Z")
        private String retrievedAt;
        @Schema(description = "Source system", example = "ReqRes")
        private String source;
    }

    @Data
    @Schema(name = "UpdateUserRequest", description = "Payload to update a User")
    public static class UpdateUserRequest {
        @Schema(description = "Remote/business id", example = "2")
        private Integer id;
        @Schema(description = "Avatar URL", example = "https://reqres.in/img/faces/2-image.jpg")
        private String avatar;
        @Schema(description = "Email", example = "janet.weaver@reqres.in")
        private String email;
        @Schema(description = "First name", example = "Janet")
        private String firstName;
        @Schema(description = "Last name", example = "Weaver")
        private String lastName;
        @Schema(description = "ISO timestamp when retrieved", example = "2025-08-27T10:00:02Z")
        private String retrievedAt;
        @Schema(description = "Source system", example = "ReqRes")
        private String source;
    }

    @Data
    @Schema(name = "IdResponse", description = "Response containing technicalId")
    public static class IdResponse {
        @Schema(description = "Technical ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public IdResponse() {}

        public IdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "IdsResponse", description = "Response containing list of technicalIds")
    public static class IdsResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;

        public IdsResponse() {}

        public IdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}