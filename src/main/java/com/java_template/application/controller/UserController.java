package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UserController - REST controller for User entity operations
 * Handles user registration, activation, and management
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

    /**
     * Create a new user (register)
     * POST /ui/user
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<User>> createUser(@RequestBody User user) {
        try {
            // Set registration timestamp
            user.setRegistrationDate(LocalDateTime.now());
            
            // Set default values if not provided
            if (user.getIsActive() == null) {
                user.setIsActive(false); // Users start inactive
            }
            if (user.getRole() == null || user.getRole().trim().isEmpty()) {
                user.setRole("EXTERNAL_SUBMITTER"); // Default role
            }

            EntityWithMetadata<User> response = entityService.create(user);
            logger.info("User created with ID: {} and email: {}", response.metadata().getId(), user.getEmail());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get user by technical UUID
     * GET /ui/user/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<User>> getUserById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.getById(id, modelSpec, User.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting User by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user by email (business identifier)
     * GET /ui/user/email/{email}
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<EntityWithMetadata<User>> getUserByEmail(@PathVariable String email) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> response = entityService.findByBusinessId(
                    modelSpec, email, "email", User.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting User by email: {}", email, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update user with optional workflow transition
     * PUT /ui/user/{id}?transition=TRANSITION_NAME
     */
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
            logger.error("Error updating User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Activate user
     * PUT /ui/user/{id}/activate
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<User>> activateUser(@PathVariable UUID id) {
        try {
            // Get current user
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> currentUser = entityService.getById(id, modelSpec, User.class);
            
            User user = currentUser.entity();
            user.setIsActive(true);
            
            EntityWithMetadata<User> response = entityService.update(id, user, "activate_user");
            logger.info("User activated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error activating User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Suspend user
     * PUT /ui/user/{id}/suspend
     */
    @PutMapping("/{id}/suspend")
    public ResponseEntity<EntityWithMetadata<User>> suspendUser(@PathVariable UUID id) {
        try {
            // Get current user
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            EntityWithMetadata<User> currentUser = entityService.getById(id, modelSpec, User.class);
            
            User user = currentUser.entity();
            user.setIsActive(false);
            
            EntityWithMetadata<User> response = entityService.update(id, user, "suspend_user");
            logger.info("User suspended with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error suspending User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete user by technical UUID
     * DELETE /ui/user/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("User deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting User", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all users
     * GET /ui/user
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<User>>> getAllUsers() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);
            List<EntityWithMetadata<User>> users = entityService.findAll(modelSpec, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error getting all Users", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search users by role
     * GET /ui/user/search/role?role=ROLE_NAME
     */
    @GetMapping("/search/role")
    public ResponseEntity<List<EntityWithMetadata<User>>> searchUsersByRole(@RequestParam String role) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.role")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(role));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<User>> users = entityService.search(modelSpec, condition, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error searching Users by role: {}", role, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search users by organization
     * GET /ui/user/search/organization?organization=ORG_NAME
     */
    @GetMapping("/search/organization")
    public ResponseEntity<List<EntityWithMetadata<User>>> searchUsersByOrganization(@RequestParam String organization) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(User.ENTITY_NAME).withVersion(User.ENTITY_VERSION);

            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.organization")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(organization));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<User>> users = entityService.search(modelSpec, condition, User.class);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error searching Users by organization: {}", organization, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
