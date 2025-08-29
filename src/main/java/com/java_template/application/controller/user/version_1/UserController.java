package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/user/v1/users")
@Tag(name = "User", description = "User entity proxy API (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create User", description = "Persist a new User entity and trigger workflows. Returns the technicalId of the created entity.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateUserRequest.class)))
    @PostMapping("")
    public ResponseEntity<CreateUserResponse> createUser(@RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            User user = new User();
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.getEmailVerified());
            user.setGdprState(request.getGdprState());
            user.setMarketingEnabled(request.getMarketingEnabled());
            user.setOwnerOfPosts(request.getOwnerOfPosts());
            user.setUserId(request.getUserId()); // may be null; workflows/services may populate

            if (request.getProfile() != null) {
                User.Profile profile = new User.Profile();
                profile.setName(request.getProfile().getName());
                profile.setLocale(request.getProfile().getLocale());
                profile.setBio(request.getProfile().getBio());
                user.setProfile(profile);
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    user
            );
            UUID entityId = idFuture.get();
            CreateUserResponse resp = new CreateUserResponse();
            resp.setTechnicalId(entityId != null ? entityId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createUser", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in createUser", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a persisted User entity by its technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(id);
            DataPayload dataPayload = itemFuture.get();

            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).build();
            }

            JsonNode dataNode = dataPayload.getData();
            UserResponse userResponse = objectMapper.treeToValue(dataNode, UserResponse.class);
            return ResponseEntity.ok(userResponse);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getUserById: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in getUserById", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in getUserById", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "List Users", description = "Retrieve a list of persisted User entities. Optionally filter by email query parameter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("")
    public ResponseEntity<List<UserResponse>> listUsers(
            @Parameter(description = "Optional email address to filter users by") @RequestParam(value = "email", required = false) String email
    ) {
        try {
            List<DataPayload> dataPayloads;
            if (email != null && !email.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.email", "EQUALS", email)
                );
                CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                        User.ENTITY_NAME,
                        User.ENTITY_VERSION,
                        condition,
                        true
                );
                dataPayloads = future.get();
            } else {
                CompletableFuture<List<DataPayload>> future = entityService.getItems(
                        User.ENTITY_NAME,
                        User.ENTITY_VERSION,
                        null, null, null
                );
                dataPayloads = future.get();
            }

            List<UserResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        UserResponse ur = objectMapper.treeToValue(payload.getData(), UserResponse.class);
                        responses.add(ur);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for listUsers: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in listUsers", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in listUsers", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User entity by technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload", required = true,
            content = @Content(schema = @Schema(implementation = UpdateUserRequest.class)))
    @PutMapping("/{technicalId}")
    public ResponseEntity<UpdateUserResponse> updateUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId,
            @RequestBody UpdateUserRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID id = UUID.fromString(technicalId);

            User user = new User();
            // Map fields - allow partial updates by setting only provided fields (controller acts as proxy)
            user.setEmail(request.getEmail());
            user.setEmailVerified(request.getEmailVerified());
            user.setGdprState(request.getGdprState());
            user.setMarketingEnabled(request.getMarketingEnabled());
            user.setOwnerOfPosts(request.getOwnerOfPosts());
            user.setUserId(request.getUserId());
            if (request.getProfile() != null) {
                User.Profile profile = new User.Profile();
                profile.setName(request.getProfile().getName());
                profile.setLocale(request.getProfile().getLocale());
                profile.setBio(request.getProfile().getBio());
                user.setProfile(profile);
            }

            CompletableFuture<UUID> updatedFuture = entityService.updateItem(id, user);
            UUID updatedId = updatedFuture.get();

            UpdateUserResponse resp = new UpdateUserResponse();
            resp.setTechnicalId(updatedId != null ? updatedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updateUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in updateUser", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in updateUser", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Delete User", description = "Delete a persisted User entity by its technicalId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<DeleteUserResponse> deleteUser(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(id);
            UUID deletedId = deletedFuture.get();
            DeleteUserResponse resp = new DeleteUserResponse();
            resp.setTechnicalId(deletedId != null ? deletedId.toString() : null);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for deleteUser: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in deleteUser", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in deleteUser", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Create multiple Users", description = "Persist multiple User entities in a batch. Returns technicalIds of created entities.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BatchCreateUserResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch user creation payload", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateUserRequest.class)),
                    examples = {@ExampleObject(value = "[{ \"email\": \"a@b.com\" }]")}))
    @PostMapping("/batch")
    public ResponseEntity<BatchCreateUserResponse> createUsersBatch(@RequestBody BatchCreateUserRequest request) {
        try {
            if (request == null || request.getUsers() == null || request.getUsers().isEmpty()) {
                throw new IllegalArgumentException("users list is required");
            }
            List<User> entities = new ArrayList<>();
            for (CreateUserRequest r : request.getUsers()) {
                User user = new User();
                user.setEmail(r.getEmail());
                user.setEmailVerified(r.getEmailVerified());
                user.setGdprState(r.getGdprState());
                user.setMarketingEnabled(r.getMarketingEnabled());
                user.setOwnerOfPosts(r.getOwnerOfPosts());
                user.setUserId(r.getUserId());
                if (r.getProfile() != null) {
                    User.Profile profile = new User.Profile();
                    profile.setName(r.getProfile().getName());
                    profile.setLocale(r.getProfile().getLocale());
                    profile.setBio(r.getProfile().getBio());
                    user.setProfile(profile);
                }
                entities.add(user);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    entities
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = new ArrayList<>();
            if (ids != null) {
                for (UUID u : ids) technicalIds.add(u != null ? u.toString() : null);
            }
            BatchCreateUserResponse resp = new BatchCreateUserResponse();
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createUsersBatch: {}", iae.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException in createUsersBatch", ee);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected exception in createUsersBatch", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(name = "CreateUserRequest", description = "Payload to create a User")
    public static class CreateUserRequest {
        @Schema(description = "User identifier (optional, may be generated by system)", example = "user-123")
        private String userId;

        @Schema(description = "Email address", required = true, example = "alice@example.com")
        private String email;

        @Schema(description = "Whether the email is already verified", example = "false")
        private Boolean emailVerified;

        @Schema(description = "GDPR state", example = "registered")
        private String gdprState;

        @Schema(description = "Marketing enabled flag", example = "true")
        private Boolean marketingEnabled;

        @Schema(description = "List of post ids owned by the user")
        private java.util.List<String> ownerOfPosts;

        @Schema(description = "Profile information")
        private ProfileDTO profile;
    }

    @Data
    @Schema(name = "CreateUserResponse", description = "Response after creating a User")
    public static class CreateUserResponse {
        @Schema(description = "Technical id of the created entity", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }

    @Data
    @Schema(name = "UserResponse", description = "Full User entity representation")
    public static class UserResponse {
        @Schema(description = "List of audit references")
        private java.util.List<String> auditRefs;

        @Schema(description = "Email address")
        private String email;

        @Schema(description = "Whether the email is verified")
        private Boolean emailVerified;

        @Schema(description = "GDPR state")
        private String gdprState;

        @Schema(description = "Marketing enabled flag")
        private Boolean marketingEnabled;

        @Schema(description = "List of post ids the user owns")
        private java.util.List<String> ownerOfPosts;

        @Schema(description = "Profile object")
        private ProfileDTO profile;

        @Schema(description = "User id")
        private String userId;
    }

    @Data
    @Schema(name = "ProfileDTO", description = "User profile information")
    public static class ProfileDTO {
        @Schema(description = "Biography", example = "Engineer and writer")
        private String bio;

        @Schema(description = "Locale", example = "en-GB")
        private String locale;

        @Schema(description = "Name", example = "Alice")
        private String name;
    }

    @Data
    @Schema(name = "UpdateUserRequest", description = "Payload to update a User")
    public static class UpdateUserRequest {
        @Schema(description = "User identifier (optional)", example = "user-123")
        private String userId;

        @Schema(description = "Email address", example = "alice@example.com")
        private String email;

        @Schema(description = "Whether the email is already verified", example = "false")
        private Boolean emailVerified;

        @Schema(description = "GDPR state", example = "registered")
        private String gdprState;

        @Schema(description = "Marketing enabled flag", example = "true")
        private Boolean marketingEnabled;

        @Schema(description = "List of post ids owned by the user")
        private java.util.List<String> ownerOfPosts;

        @Schema(description = "Profile information")
        private ProfileDTO profile;
    }

    @Data
    @Schema(name = "UpdateUserResponse", description = "Response after updating a User")
    public static class UpdateUserResponse {
        @Schema(description = "Technical id of the updated entity", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteUserResponse", description = "Response after deleting a User")
    public static class DeleteUserResponse {
        @Schema(description = "Technical id of the deleted entity", example = "0f8fad5b-d9cb-469f-a165-70867728950e")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchCreateUserRequest", description = "Batch payload to create multiple Users")
    public static class BatchCreateUserRequest {
        @Schema(description = "List of users to create", required = true)
        private List<CreateUserRequest> users;
    }

    @Data
    @Schema(name = "BatchCreateUserResponse", description = "Response after creating multiple Users")
    public static class BatchCreateUserResponse {
        @Schema(description = "List of technical ids of created entities")
        private List<String> technicalIds;
    }
}