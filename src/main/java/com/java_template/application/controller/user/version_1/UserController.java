package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;

/**
 * Dull proxy controller for User entity.
 * All business logic is implemented in workflows; the controller only proxies requests to EntityService.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User entity API")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create User", description = "Create a new User entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = UserCreateRequest.class)))
            @RequestBody UserCreateRequest request
    ) {
        try {
            // Basic request format validation
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            // Build payload to persist - controller does not implement business logic, just proxies data
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("name", request.getName());
            payload.put("email", request.getEmail());
            if (request.getSubscribed() != null) {
                payload.put("subscribed", request.getSubscribed());
            } else {
                payload.putNull("subscribed");
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    payload
            );

            UUID technicalId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create user: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when creating user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when creating user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get User", description = "Retrieve a User entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<com.fasterxml.jackson.databind.node.ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                throw new NoSuchElementException("User not found");
            }

            UserResponse resp = objectMapper.treeToValue(node, UserResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to get user: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException when retrieving user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted when retrieving user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception ex) {
            logger.error("Unexpected error when retrieving user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Static DTO classes matching functional requirements

    @Data
    @Schema(name = "UserCreateRequest", description = "Payload to create a User")
    public static class UserCreateRequest {
        @Schema(description = "Name of the user", example = "Alice")
        private String name;

        @Schema(description = "Email of the user", example = "alice@example.com")
        private String email;

        @Schema(description = "Subscribe to notifications", example = "true")
        private Boolean subscribed;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the created entity", example = "user-xyz-789")
        private String technicalId;
    }

    @Data
    @Schema(name = "UserResponse", description = "User entity response")
    public static class UserResponse {
        @Schema(description = "Business user id", example = "u-1001")
        private String userId;

        @Schema(description = "Name of the user", example = "Alice")
        private String name;

        @Schema(description = "Email of the user", example = "alice@example.com")
        private String email;

        @Schema(description = "Subscribed to notifications", example = "true")
        private Boolean subscribed;

        @Schema(description = "Role of the user", example = "regular")
        private String role;
    }
}