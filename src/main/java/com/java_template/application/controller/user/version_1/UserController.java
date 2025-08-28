```java
package com.java_template.application.controller.user.version_1;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/users")
@Tag(name = "User Controller", description = "API for managing users")
public class UserController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "Create a new user", description = "Creates a new user and returns the technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User created successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<UserResponse> createUser(@RequestBody UserRequest userRequest) {
        try {
            User user = new User();
            user.setName(userRequest.getName());
            user.setEmail(userRequest.getEmail());
            CompletableFuture<UUID> idFuture = entityService.addItem(User.ENTITY_NAME, User.ENTITY_VERSION, user);
            UUID entityId = idFuture.get();
            return ResponseEntity.ok(new UserResponse(entityId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    @Operation(summary = "Retrieve a user by technical ID", description = "Returns user details for the specified technical ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User retrieved successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> getUser(@Parameter(name = "technicalId", description = "Technical ID of the user") @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload != null) {
                User user = objectMapper.treeToValue(dataPayload.getData(), User.class);
                return ResponseEntity.ok(new UserResponse(user.getId(), user.getName(), user.getEmail()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // DTOs for request and response
    static class UserRequest {
        @Schema(description = "Name of the user", example = "John Doe")
        private String name;

        @Schema(description = "Email of the user", example = "john@example.com")
        private String email;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    static class UserResponse {
        @Schema(description = "Technical ID of the user", example = "userId123")
        private String technicalId;
        private String name;
        private String email;

        public UserResponse(String technicalId) {
            this.technicalId = technicalId;
        }

        public UserResponse(String technicalId, String name, String email) {
            this.technicalId = technicalId;
            this.name = name;
            this.email = email;
        }

        // Getters
        public String getTechnicalId() {
            return technicalId;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }
}
```