package com.java_template.application.controller;

import com.java_template.application.entity.user.version_1.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
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
 * UserController - REST API for User entity management
 * Handles CRUD operations and user lifecycle management
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
    public ResponseEntity<EntityWithMetadata<User>> createUser(@Valid @RequestBody User user) {
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

    @GetMapping("/business/{userId}")
    public ResponseEntity<EntityWithMetadata<User>> getUserByBusinessId(@PathVariable String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.findByBusinessId(
                    modelSpec, userId, "userId", User.class);

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
            @Valid @RequestBody User user,
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

    @GetMapping("/search")
    public ResponseEntity<PageResult<EntityWithMetadata<User>>> searchUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            
            List<org.cyoda.cloud.api.event.common.condition.QueryCondition> conditions = new java.util.ArrayList<>();

            if (email != null && !email.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(email)));
            }

            if (status != null && !status.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.status")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(status)));
            }

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<User>> result;
            if (conditions.isEmpty()) {
                result = entityService.findAll(modelSpec, User.class, params);
            } else {
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);
                result = entityService.search(modelSpec, condition, User.class, params);
            }

            logger.info("Search returned {} users", result.data().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search users: %s", e.getMessage())
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

