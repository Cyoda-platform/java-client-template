package com.java_template.application.controller;

import com.java_template.application.entity.user_account.version_1.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
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
 * REST Controller for UserAccount entity
 * Provides user management and RBAC endpoints
 */
@RestController
@RequestMapping("/ui/users")
@CrossOrigin(origins = "*")
public class UserAccountController {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UserAccountController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<UserAccount>> createUser(@RequestBody UserAccount user) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(UserAccount.ENTITY_NAME).withVersion(UserAccount.ENTITY_VERSION);
            EntityWithMetadata<UserAccount> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, user.getUserId(), "userId", UserAccount.class);

            if (existing != null) {
                logger.warn("User with ID {} already exists", user.getUserId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("User already exists with ID: %s", user.getUserId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<UserAccount> response = entityService.create(user);
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
    public ResponseEntity<EntityWithMetadata<UserAccount>> getUserById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(UserAccount.ENTITY_NAME).withVersion(UserAccount.ENTITY_VERSION);
            EntityWithMetadata<UserAccount> response = entityService.getById(id, modelSpec, UserAccount.class);
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
    public ResponseEntity<EntityWithMetadata<UserAccount>> updateUser(
            @PathVariable UUID id,
            @RequestBody UserAccount user,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<UserAccount> response = entityService.update(id, user, transition);
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

    @GetMapping("/search/by-role")
    public ResponseEntity<List<EntityWithMetadata<UserAccount>>> searchByRole(@RequestParam String role) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(UserAccount.ENTITY_NAME).withVersion(UserAccount.ENTITY_VERSION);

            SimpleCondition roleCondition = new SimpleCondition()
                    .withJsonPath("$.role")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(role));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(roleCondition));

            PageResult<EntityWithMetadata<UserAccount>> result = entityService.search(
                    modelSpec,
                    condition,
                    UserAccount.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1000)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            logger.info("Found {} users with role '{}'", result.data().size(), role);
            return ResponseEntity.ok(result.data());
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

