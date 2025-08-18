package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/users")
@Tag(name = "User API", description = "Endpoints for managing users")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create User", description = "Create a new user. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        try {
            if (request.getName() == null || request.getName().isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                throw new IllegalArgumentException("email is required");
            }

            User user = new User();
            user.setId(request.getId());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setContact(request.getContact());
            user.setRole(request.getRole());
            user.setFavorites(request.getFavorites());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    user
            );
            UUID technicalId = idFuture.get();

            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to create user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while creating user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while creating user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    @Operation(summary = "Get User", description = "Retrieve a user by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    User.ENTITY_NAME,
                    String.valueOf(User.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            UserResponse resp = objectMapper.treeToValue(node, UserResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request to get user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", cause.getMessage()));
            } else {
                logger.error("Execution exception while retrieving user", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("INTERNAL_ERROR", e.getMessage()));
        }
    }

    // DTOs
    @Data
    static class CreateUserRequest {
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "User name", required = true)
        private String name;
        @Schema(description = "Email", required = true)
        private String email;
        @Schema(description = "Contact phone")
        private String contact;
        @Schema(description = "Role")
        private String role;
        @Schema(description = "Favorite pet technicalIds")
        private List<String> favorites;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id assigned to the entity")
        private String technicalId;
    }

    @Data
    static class UserResponse {
        @Schema(description = "Technical id")
        private String technicalId;
        @Schema(description = "Business id (optional)")
        private String id;
        @Schema(description = "Name")
        private String name;
        @Schema(description = "Email")
        private String email;
        @Schema(description = "Contact")
        private String contact;
        @Schema(description = "Role")
        private String role;
        @Schema(description = "Favorites list")
        private List<String> favorites;
        @Schema(description = "Lifecycle state")
        private String lifecycleState;
        @Schema(description = "Created at")
        private String createdAt;
        @Schema(description = "Updated at")
        private String updatedAt;
    }

    @Data
    static class ErrorResponse {
        private final String code;
        private final String message;
    }
}
