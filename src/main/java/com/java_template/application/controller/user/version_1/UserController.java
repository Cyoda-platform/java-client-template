package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User entity proxy controller (version 1)")
@Validated
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create User", description = "Persist a new User entity and return its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User creation payload",
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class))
            )
            @Valid @RequestBody CreateUserRequest request
    ) {
        try {
            // Build entity (controller remains a simple proxy; generate technical userId)
            User user = new User();
            user.setUserId(UUID.randomUUID().toString());
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.getEmailVerified());
            if (request.getProfile() != null) {
                User.Profile p = new User.Profile();
                p.setName(request.getProfile().getName());
                p.setBio(request.getProfile().getBio());
                p.setLocale(request.getProfile().getLocale());
                user.setProfile(p);
            }

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
            );
            UUID technicalId = idFuture.get();
            CreateUserResponse resp = new CreateUserResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createUser", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in createUser", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createUser", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Users", description = "Persist multiple User entities and return their technicalIds")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateUserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<List<CreateUserResponse>> createUsersBatch(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Batch user creation payload",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateUserRequest.class)))
            )
            @Valid @RequestBody List<CreateUserRequest> requests
    ) {
        try {
            List<User> users = new ArrayList<>();
            for (CreateUserRequest r : requests) {
                User user = new User();
                user.setUserId(UUID.randomUUID().toString());
                user.setEmail(r.getEmail());
                user.setEmailVerified(r.getEmailVerified());
                if (r.getProfile() != null) {
                    User.Profile p = new User.Profile();
                    p.setName(r.getProfile().getName());
                    p.setBio(r.getProfile().getBio());
                    p.setLocale(r.getProfile().getLocale());
                    user.setProfile(p);
                }
                users.add(user);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    users
            );
            List<UUID> ids = idsFuture.get();
            List<CreateUserResponse> responses = new ArrayList<>();
            for (UUID id : ids) {
                CreateUserResponse r = new CreateUserResponse();
                r.setTechnicalId(id.toString());
                responses.add(r);
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createUsersBatch", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in createUsersBatch", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating users batch", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createUsersBatch", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User entity by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(404).build();
            }
            UserResponse resp = objectMapper.convertValue(node, UserResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in getUserById", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in getUserById", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting user", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getUserById", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List all Users", description = "Retrieve all persisted User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<UserResponse> list = objectMapper.convertValue(arrayNode, new TypeReference<List<UserResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in listUsers", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing users", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in listUsers", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Search Users by condition", description = "Retrieve Users matching a simple search condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsersByCondition(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Search condition request",
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class))
            )
            @Valid @RequestBody SearchConditionRequest condition
    ) {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arrayNode = itemsFuture.get();
            List<UserResponse> list = objectMapper.convertValue(arrayNode, new TypeReference<List<UserResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid search condition", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in searchUsersByCondition", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching users", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in searchUsersByCondition", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<CreateUserResponse> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User update payload",
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class))
            )
            @Valid @RequestBody CreateUserRequest request
    ) {
        try {
            User user = new User();
            user.setUserId(technicalId);
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.getEmailVerified());
            if (request.getProfile() != null) {
                User.Profile p = new User.Profile();
                p.setName(request.getProfile().getName());
                p.setBio(request.getProfile().getBio());
                p.setLocale(request.getProfile().getLocale());
                user.setProfile(p);
            }

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId),
                    user
            );
            UUID updated = updatedIdFuture.get();
            CreateUserResponse resp = new CreateUserResponse();
            resp.setTechnicalId(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateUser", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in updateUser", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating user", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser", e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<CreateUserResponse> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            UUID deleted = deletedIdFuture.get();
            CreateUserResponse resp = new CreateUserResponse();
            resp.setTechnicalId(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument in deleteUser", e);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("Execution exception in deleteUser", e);
                return ResponseEntity.status(500).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting user", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error in deleteUser", e);
            return ResponseEntity.status(500).build();
        }
    }

    // --- DTOs ---

    @Data
    public static class CreateUserRequest {
        @NotBlank
        @Schema(description = "Email address of the user", example = "alice@example.com")
        private String email;

        @Schema(description = "Whether email is verified", example = "false")
        private Boolean emailVerified = false;

        @Schema(description = "Profile object")
        private ProfileDto profile;

        @Data
        public static class ProfileDto {
            @Schema(description = "Display name", example = "Alice")
            private String name;
            @Schema(description = "Biography", example = "Bio text")
            private String bio;
            @Schema(description = "Locale", example = "en-GB")
            private String locale;
        }
    }

    @Data
    public static class CreateUserResponse {
        @Schema(description = "Technical identifier of the created entity", example = "a3f1e6d1-...")
        private String technicalId;
    }

    @Data
    public static class UserResponse {
        @Schema(description = "Technical user id")
        private String userId;

        @Schema(description = "References to audits")
        private List<String> auditRefs;

        @Schema(description = "Email")
        private String email;

        @Schema(description = "Email verified")
        private Boolean emailVerified;

        @Schema(description = "GDPR state")
        private String gdprState;

        @Schema(description = "Marketing enabled")
        private Boolean marketingEnabled;

        @Schema(description = "Owner of posts")
        private List<String> ownerOfPosts;

        @Schema(description = "Profile object")
        private Profile profile;

        @Data
        public static class Profile {
            @Schema(description = "Name")
            private String name;
            @Schema(description = "Bio")
            private String bio;
            @Schema(description = "Locale")
            private String locale;
        }
    }
}