package com.example.application.controller;

import com.example.application.entity.user.version_1.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * UserController - REST API for User management
 * 
 * Provides endpoints for creating, retrieving, updating, and deleting users.
 * Supports Auth0 sync workflow transitions.
 */
@RestController
@RequestMapping("/ui/user")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<User>> createUser(@RequestBody User user) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, user.getUserId(), "userId", User.class);

            if (existing != null) {
                logger.warn("User with ID {} already exists", user.getUserId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("User already exists with ID: %s", user.getUserId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<User> response = entityService.create(user);
            logger.info("User created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create user: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> getUserById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.getById(id, modelSpec, User.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve user: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> updateUser(
            @PathVariable UUID id,
            @RequestBody User user,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<User> response = entityService.update(id, user, transition);
            logger.info("User updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update user: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/sync-auth0")
    public ResponseEntity<EntityWithMetadata<User>> syncFromAuth0(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> current = entityService.getById(id, modelSpec, User.class);

            EntityWithMetadata<User> response = entityService.update(id, current.entity(), "SYNC_FROM_AUTH0");
            logger.info("User synced from Auth0 with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to sync user: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("User deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete user: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

