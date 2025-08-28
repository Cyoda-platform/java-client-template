```java
package com.java_template.application.controller.user.version_1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.java_template.application.entity.user.version_1.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Create a new user", description = "Creates a new user and returns the technical ID.")
    @PostMapping
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<UserResponse> createUser(@RequestBody UserRequest userRequest) {
        try {
            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setName(userRequest.getName());
            user.setEmail(userRequest.getEmail());
            user.setPhone(userRequest.getPhone());
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
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Retrieve a user by technical ID", description = "Fetches a user by their technical ID.")
    @GetMapping("/{technicalId}")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = User.class))),
        @ApiResponse(responseCode = "404", description = "User Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    public ResponseEntity<User> getUser(@Parameter(name = "technicalId", description = "Technical ID of the user") @PathVariable String technicalId) {
        try {
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            JsonNode data = dataPayload != null ? dataPayload.getData() : null;
            User user = objectMapper.treeToValue(data, User.class);
            return ResponseEntity.ok(user);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().build();
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    static class UserRequest {
        private String name;
        private String email;
        private String phone;
    }

    @Data
    static class UserResponse {
        private String technicalId;

        public UserResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
```