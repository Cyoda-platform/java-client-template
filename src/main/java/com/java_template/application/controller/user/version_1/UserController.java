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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User API", description = "Controller proxy for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Create a new User entity. Returns technicalId on success.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload")
                                                         @RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            User user = new User();
            // Basic mapping from request to entity. No business logic here.
            user.setId(request.getId());
            user.setTechnicalId(request.getTechnicalId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setRole(request.getRole());
            user.setContact(request.getContact());
            user.setVerified(request.getVerified());
            user.setVerificationStatus(request.getVerificationStatus());
            user.setSavedPets(request.getSavedPets());
            user.setNotes(request.getNotes());
            user.setCreatedAt(request.getCreatedAt());
            user.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
            );

            UUID technicalId = idFuture.get();

            CreateUserResponse resp = new CreateUserResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createUser request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createUser", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Create multiple Users", description = "Create multiple User entities in bulk. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkCreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<BulkCreateUserResponse> createUsersBulk(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk user creation payload")
                                                                 @RequestBody List<CreateUserRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one user");
            }

            List<User> users = new ArrayList<>();
            for (CreateUserRequest request : requests) {
                User user = new User();
                user.setId(request.getId());
                user.setTechnicalId(request.getTechnicalId());
                user.setName(request.getName());
                user.setEmail(request.getEmail());
                user.setRole(request.getRole());
                user.setContact(request.getContact());
                user.setVerified(request.getVerified());
                user.setVerificationStatus(request.getVerificationStatus());
                user.setSavedPets(request.getSavedPets());
                user.setNotes(request.getNotes());
                user.setCreatedAt(request.getCreatedAt());
                user.setUpdatedAt(request.getUpdatedAt());
                users.add(user);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    users
            );

            List<UUID> ids = idsFuture.get();
            BulkCreateUserResponse resp = new BulkCreateUserResponse();
            List<String> technicalIds = new ArrayList<>();
            for (UUID id : ids) {
                technicalIds.add(id.toString());
            }
            resp.setTechnicalIds(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createUsersBulk request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createUsersBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createUsersBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a persisted User entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<GetUserResponse> getUserById(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                                       @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    tid
            );

            ObjectNode entityNode = itemFuture.get();
            GetUserResponse resp = new GetUserResponse();
            resp.setTechnicalId(technicalId);
            resp.setEntity(entityNode);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getUserById request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getUserById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getUserById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "List all Users", description = "Retrieve all persisted User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<ArrayNode> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                logger.error("ExecutionException in getAllUsers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Users", description = "Search Users by simple field-based conditions (AND group).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<ArrayNode> searchUsers(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of simple conditions to AND together")
                                                 @RequestBody List<SearchFilter> filters) {
        try {
            if (filters == null || filters.isEmpty()) {
                throw new IllegalArgumentException("At least one filter is required");
            }

            List<Condition> conds = new ArrayList<>();
            for (SearchFilter f : filters) {
                if (f.getField() == null || f.getOperator() == null) {
                    throw new IllegalArgumentException("Filter must contain field and operator");
                }
                String path = "$." + f.getField();
                conds.add(Condition.of(path, f.getOperator(), f.getValue()));
            }

            // Build grouped condition using AND
            SearchConditionRequest condition = SearchConditionRequest.group("AND", conds.toArray(new Condition[0]));

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode results = filteredItemsFuture.get();
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchUsers request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                logger.error("ExecutionException in searchUsers", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<UpdateUserResponse> updateUser(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                                         @PathVariable String technicalId,
                                                         @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload")
                                                         @RequestBody CreateUserRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            UUID tid = UUID.fromString(technicalId);

            User user = new User();
            user.setId(request.getId());
            user.setTechnicalId(request.getTechnicalId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setRole(request.getRole());
            user.setContact(request.getContact());
            user.setVerified(request.getVerified());
            user.setVerificationStatus(request.getVerificationStatus());
            user.setSavedPets(request.getSavedPets());
            user.setNotes(request.getNotes());
            user.setCreatedAt(request.getCreatedAt());
            user.setUpdatedAt(request.getUpdatedAt());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    tid,
                    user
            );

            UUID updatedId = updatedIdFuture.get();
            UpdateUserResponse resp = new UpdateUserResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateUser request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in updateUser", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete User", description = "Delete an existing User by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<DeleteUserResponse> deleteUser(@Parameter(name = "technicalId", description = "Technical ID of the entity")
                                                         @PathVariable String technicalId) {
        try {
            UUID tid = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    tid
            );

            UUID deletedId = deletedIdFuture.get();
            DeleteUserResponse resp = new DeleteUserResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid deleteUser request: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in deleteUser", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes

    @Data
    @Schema(name = "CreateUserRequest", description = "Payload to create or update a User")
    public static class CreateUserRequest {
        @Schema(description = "Business id (e.g. USR-99)")
        private String id;
        @Schema(description = "Optional technicalId (ignored on create)")
        private String technicalId;
        @Schema(description = "User name")
        private String name;
        @Schema(description = "User email")
        private String email;
        @Schema(description = "User role (customer/staff/admin)")
        private String role;
        @Schema(description = "Contact phone")
        private String contact;
        @Schema(description = "Verified flag")
        private Boolean verified;
        @Schema(description = "Verification status (unverified/pending/verified/rejected)")
        private String verificationStatus;
        @Schema(description = "Saved pet business ids")
        private List<String> savedPets;
        @Schema(description = "Internal notes")
        private String notes;
        @Schema(description = "Created at timestamp")
        private String createdAt;
        @Schema(description = "Updated at timestamp")
        private String updatedAt;
    }

    @Data
    @Schema(name = "CreateUserResponse", description = "Response after creating a User")
    public static class CreateUserResponse {
        @Schema(description = "Technical id assigned by datastore")
        private String technicalId;
    }

    @Data
    @Schema(name = "BulkCreateUserResponse", description = "Response after bulk creating Users")
    public static class BulkCreateUserResponse {
        @Schema(description = "List of technical ids assigned by datastore")
        private List<String> technicalIds;
    }

    @Data
    @Schema(name = "GetUserResponse", description = "Response for get User by technicalId")
    public static class GetUserResponse {
        @Schema(description = "Technical id of the entity")
        private String technicalId;
        @Schema(description = "Persisted entity as stored", implementation = ObjectNode.class)
        private ObjectNode entity;
    }

    @Data
    @Schema(name = "UpdateUserResponse", description = "Response after updating a User")
    public static class UpdateUserResponse {
        @Schema(description = "Technical id of the updated entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteUserResponse", description = "Response after deleting a User")
    public static class DeleteUserResponse {
        @Schema(description = "Technical id of the deleted entity")
        private String technicalId;
    }

    @Data
    @Schema(name = "SearchFilter", description = "Simple field-based search filter")
    public static class SearchFilter {
        @Schema(description = "Field name on the User entity (e.g. email, role)")
        private String field;
        @Schema(description = "Operator (EQUALS, NOT_EQUAL, IEQUALS, GREATER_THAN, LESS_THAN, etc.)")
        private String operator;
        @Schema(description = "Value to compare with (string representation)")
        private String value;
    }
}