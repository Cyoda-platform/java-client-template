package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dull proxy controller for User entity. All business logic lives in workflows.
 */
@RestController
@RequestMapping("/api/user/v1")
@Tag(name = "User", description = "User entity proxy endpoints (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get User by technicalId", description = "Retrieve a User entity by its technical UUID identifier")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/users/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    uuid
            );
            ObjectNode result = itemFuture.get();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in getUserById: {}", iae.getMessage(), iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving user with id {}: {}", technicalId, ie.getMessage(), ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getUserById for id {}: {}", technicalId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get all Users", description = "Retrieve all User entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllUsers() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION)
            );
            ArrayNode result = itemsFuture.get();
            return ResponseEntity.ok(result);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving users: {}", ie.getMessage(), ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error in getAllUsers: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Entity not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("Execution exception: {}", ee.getMessage(), ee);
            String msg = cause != null ? cause.getMessage() : ee.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(msg);
        }
    }

    @Schema(name = "UserResponse", description = "User response payload")
    @Data
    public static class UserResponse {
        @Schema(description = "Source ID", example = "42")
        private Integer id;

        @Schema(description = "Full name", example = "John Doe")
        private String fullName;

        @Schema(description = "Username", example = "jdoe")
        private String username;

        @Schema(description = "Email address", example = "jdoe@example.com")
        private String email;

        @Schema(description = "Phone", example = "+1-555-0100")
        private String phone;

        @Schema(description = "Address", example = "123 Main St")
        private String address;

        @Schema(description = "Timestamp when fetched from source", example = "2025-09-01T00:01:00Z")
        private String sourceFetchedAt;

        @Schema(description = "Validation status", example = "VALID")
        private String validationStatus;

        @Schema(description = "Timestamp when transformed", example = "2025-09-01T00:02:00Z")
        private String transformedAt;

        @Schema(description = "Timestamp when stored", example = "2025-09-01T00:03:00Z")
        private String storedAt;
    }
}