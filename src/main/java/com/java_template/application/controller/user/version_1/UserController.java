package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.Data;

@RestController
@RequestMapping("/users/v1")
@Tag(name = "UserController", description = "Controller proxy for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Create a new User entity and start workflows. Returns technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User create payload", required = true,
            content = @Content(schema = @Schema(implementation = UserCreateRequest.class)))
        @RequestBody UserCreateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            User user = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                user
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createUser", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during createUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Batch create Users", description = "Create multiple Users. Returns list of technicalIds.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchTechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createUsersBatch(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "List of user create payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserCreateRequest.class))))
        @RequestBody List<UserCreateRequest> requests
    ) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request body must contain at least one user");
            }
            List<User> users = new ArrayList<>();
            for (UserCreateRequest r : requests) {
                users.add(toEntity(r));
            }
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                users
            );
            List<UUID> ids = idsFuture.get();
            List<String> techIds = new ArrayList<>();
            for (UUID id : ids) {
                techIds.add(id.toString());
            }
            BatchTechnicalIdResponse resp = new BatchTechnicalIdResponse();
            resp.setTechnicalIds(techIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for createUsersBatch", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during createUsersBatch", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during createUsersBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during createUsersBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUserByTechnicalId(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }
            UserResponse resp = nodeToUserResponse(node, technicalId);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for getUserByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during getUserByTechnicalId", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getUserByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getUserByTechnicalId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all User entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<UserResponse> respList = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    ObjectNode node = (ObjectNode) array.get(i);
                    String techId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                    respList.add(nodeToUserResponse(node, techId));
                }
            }
            return ResponseEntity.ok(respList);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            logger.error("Execution exception during getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during getAllUsers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update User", description = "Update an existing User entity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User update payload", required = true,
            content = @Content(schema = @Schema(implementation = UserCreateRequest.class)))
        @RequestBody UserCreateRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            UUID uuid = UUID.fromString(technicalId);
            User user = toEntity(request);
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid,
                user
            );
            UUID updatedId = updatedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request for updateUser", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during updateUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during updateUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete User", description = "Delete a User by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                uuid
            );
            UUID deletedId = deletedFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId for deleteUser", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Execution exception during deleteUser", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted during deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during deleteUser", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private User toEntity(UserCreateRequest request) {
        User user = new User();
        // Map fields conservatively; do not implement business logic here
        user.setId(request.getId());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setDefaultAddressId(request.getDefaultAddressId());
        user.setStatus(request.getStatus());
        return user;
    }

    private UserResponse nodeToUserResponse(ObjectNode node, String technicalId) {
        UserResponse resp = new UserResponse();
        if (technicalId != null) {
            resp.setTechnicalId(technicalId);
        } else if (node.has("technicalId")) {
            resp.setTechnicalId(node.get("technicalId").asText(null));
        } else if (node.has("technical_id")) {
            resp.setTechnicalId(node.get("technical_id").asText(null));
        }
        resp.setId(node.has("id") ? node.get("id").asText(null) : null);
        resp.setEmail(node.has("email") ? node.get("email").asText(null) : null);
        resp.setName(node.has("name") ? node.get("name").asText(null) : null);
        resp.setPhone(node.has("phone") ? node.get("phone").asText(null) : null);
        resp.setDefaultAddressId(node.has("defaultAddressId") ? node.get("defaultAddressId").asText(null) :
            node.has("default_address_id") ? node.get("default_address_id").asText(null) : null);
        resp.setStatus(node.has("status") ? node.get("status").asText(null) : null);
        return resp;
    }

    // DTOs

    @Data
    @Schema(name = "UserCreateRequest", description = "Payload to create or update a User")
    public static class UserCreateRequest {
        @Schema(description = "Business id of the user", example = "U-1")
        private String id;
        @Schema(description = "Email of the user", example = "alice@example.com")
        private String email;
        @Schema(description = "Full name", example = "Alice")
        private String name;
        @Schema(description = "Phone number", example = "+1234567890")
        private String phone;
        @Schema(description = "Default address id", example = "A-1", nullable = true)
        private String defaultAddressId;
        @Schema(description = "User status", example = "Active")
        private String status;
    }

    @Data
    @Schema(name = "UserResponse", description = "User representation returned by the API")
    public static class UserResponse {
        @Schema(description = "Technical id", example = "tech-user-u1")
        private String technicalId;
        @Schema(description = "Business id", example = "U-1")
        private String id;
        @Schema(description = "Email", example = "alice@example.com")
        private String email;
        @Schema(description = "Full name", example = "Alice")
        private String name;
        @Schema(description = "Phone number", example = "+1234567890")
        private String phone;
        @Schema(description = "Default address id", example = "A-1", nullable = true)
        private String defaultAddressId;
        @Schema(description = "User status", example = "Active")
        private String status;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technical id")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "tech-user-u1")
        private String technicalId;
    }

    @Data
    @Schema(name = "BatchTechnicalIdResponse", description = "Response containing list of technical ids")
    public static class BatchTechnicalIdResponse {
        @Schema(description = "List of technical ids")
        private List<String> technicalIds;
    }
}