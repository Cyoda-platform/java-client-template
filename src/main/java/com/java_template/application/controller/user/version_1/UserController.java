package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.user.version_1.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/users")
@Tag(name = "User Controller", description = "Controller proxy for User entity (version 1)")
@Validated
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Persist a new User entity and trigger related workflows (controller proxies request to EntityService).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "User create request",
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class)))
            @Valid @RequestBody CreateUserRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Convert request DTO to ObjectNode to pass to EntityService (controller must be a proxy; no business logic here)
            ObjectNode data = MAPPER.convertValue(request, ObjectNode.class);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();

            CreateUserResponse response = new CreateUserResponse();
            response.setTechnicalId(technicalId.toString());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create user: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while creating user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception e) {
            logger.error("Unexpected error while creating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error"));
        }
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User entity by technicalId (controller proxies to EntityService).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping(value = "/{technicalId}", produces = "application/json")
    public ResponseEntity<?> getUserByTechnicalId(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId path parameter is required");
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );

            ObjectNode item = itemFuture.get();

            if (item == null || item.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }

            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get user: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", iae.getMessage()));
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted while retrieving user", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Interrupted"));
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Internal error"));
        }
    }

    private ResponseEntity<?> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.info("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", cause.getMessage()));
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Invalid argument from execution: {}", cause.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
        } else {
            logger.error("Execution exception occurred", ee);
            String msg = cause != null && cause.getMessage() != null ? cause.getMessage() : "Execution error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", msg));
        }
    }

    // Static DTOs for request/response payloads

    @Data
    @Schema(name = "CreateUserRequest", description = "Request payload to create a new User")
    public static class CreateUserRequest {
        @NotBlank
        @Schema(description = "Full name of the user", example = "Alex Smith", required = true)
        private String name;

        @NotBlank
        @Email
        @Schema(description = "Contact email", example = "alex@example.com", required = true)
        private String email;

        @Schema(description = "Contact phone", example = "+15551234")
        private String phone;

        @Schema(description = "Shipping/contact address", example = "123 Pet Lane")
        private String address;

        @Schema(description = "Role of the user", example = "customer")
        private String role;
    }

    @Data
    @Schema(name = "CreateUserResponse", description = "Response after creating a User")
    public static class CreateUserResponse {
        @Schema(description = "Technical ID of the created user", example = "user-tech-456")
        private String technicalId;
    }

    @Data
    @Schema(name = "UserResponse", description = "User entity representation returned by GET")
    public static class UserResponse {
        @Schema(description = "Business id", example = "user-123")
        private String id;

        @Schema(description = "Full name of the user", example = "Alex Smith")
        private String name;

        @Schema(description = "Contact email", example = "alex@example.com")
        private String email;

        @Schema(description = "Whether the user is verified", example = "false")
        private Boolean verified;

        @Schema(description = "ISO timestamp when user was created", example = "2025-08-01T12:00:00Z")
        private String createdAt;
    }
}