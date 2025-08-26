package com.java_template.application.controller.user.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.service.EntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller is a thin proxy to the User entity service.
 * All business logic is handled in workflows; controller only delegates.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "User Controller", description = "CRUD endpoints for User entity (version 1)")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Create a new User entity. Returns the technicalId of the created entity.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateUserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "User creation payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateUserRequest.class)))
        @RequestBody CreateUserRequest request
    ) {
        try {
            // Basic request format validation (no business logic)
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (isNullOrEmpty(request.getId())) {
                throw new IllegalArgumentException("id is required");
            }
            if (isNullOrEmpty(request.getName())) {
                throw new IllegalArgumentException("name is required");
            }
            if (isNullOrEmpty(request.getEmail())) {
                throw new IllegalArgumentException("email is required");
            }
            if (isNullOrEmpty(request.getPhone())) {
                throw new IllegalArgumentException("phone is required");
            }

            // Map request to entity (no business logic)
            User user = new User();
            user.setId(request.getId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setAddress(request.getAddress());
            user.setPreferences(request.getPreferences());

            UUID technicalId = entityService.addItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                user
            ).get();

            CreateUserResponse response = new CreateUserResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to create User: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Error executing createUser", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in createUser", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Operation(summary = "Get User", description = "Retrieve a User entity by technicalId.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GetUserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(
        @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
        @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (isNullOrEmpty(technicalId)) {
                throw new IllegalArgumentException("technicalId is required");
            }
            UUID id = UUID.fromString(technicalId);

            ObjectNode entityNode = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                id
            ).get();

            GetUserResponse response = new GetUserResponse();
            response.setTechnicalId(technicalId);
            response.setEntity(entityNode);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request to get User: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("Error executing getUser", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in getUser", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    // Utility to check empty strings
    private boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // --- DTOs ---

    @Data
    @Schema(name = "CreateUserRequest", description = "Request payload to create a User")
    public static class CreateUserRequest {
        @Schema(description = "Business id", required = true, example = "user-123")
        private String id;

        @Schema(description = "Full name", required = true, example = "Jane Doe")
        private String name;

        @Schema(description = "Email address", required = true, example = "jane@example.com")
        private String email;

        @Schema(description = "Phone number", required = true, example = "+123456789")
        private String phone;

        @Schema(description = "Address (optional)", required = false, example = "123 Main St")
        private String address;

        @Schema(description = "Preferences (optional)", required = false)
        private List<String> preferences;
    }

    @Data
    @Schema(name = "CreateUserResponse", description = "Response after creating a User")
    public static class CreateUserResponse {
        @Schema(description = "Technical identifier (UUID) of the created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    @Schema(name = "GetUserResponse", description = "Response when retrieving a User")
    public static class GetUserResponse {
        @Schema(description = "Technical identifier (UUID) of the entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        @Schema(description = "Entity payload as returned by the store")
        private ObjectNode entity;
    }
}