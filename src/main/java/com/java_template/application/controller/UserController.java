package com.java_template.application.controller;

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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/users")
@Tag(name = "User Controller", description = "Endpoints for User entity")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get User", description = "Retrieve a User by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(value = "/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUser(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode node = itemFuture.get();
            User user = objectMapper.treeToValue(node, User.class);

            UserResponse resp = new UserResponse();
            resp.setUserId(user.getUserId());
            resp.setDisplayName(user.getDisplayName());
            resp.setPreferences(user.getPreferences());
            resp.setOptInReports(user.getOptInReports());
            resp.setLastActiveAt(user.getLastActiveAt());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            logger.warn("Validation error getting user: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching user", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unexpected error fetching user", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    @Data
    static class UserResponse {
        @Schema(description = "Application user id")
        private String userId;
        @Schema(description = "Display name")
        private String displayName;
        @Schema(description = "Preferences (JSON string)")
        private String preferences;
        @Schema(description = "Opt-in for reports")
        private Boolean optInReports;
        @Schema(description = "Last active timestamp")
        private String lastActiveAt;
    }
}
