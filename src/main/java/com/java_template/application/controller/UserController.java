package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserController - REST API for user management and preferences
 * 
 * This controller provides endpoints for:
 * - Creating and updating user profiles
 * - Managing user preferences and settings
 * - Generating personalized recommendations
 * - Tracking user activity and engagement
 */
@RestController
@RequestMapping("/ui/users")
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
     * Create a new user
     */
    @PostMapping
    public ResponseEntity<UUID> createUser(@RequestBody User user) {
        logger.info("Creating new user: {}", user.getUsername());

        try {
            // Set creation timestamp and default status
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setStatus("active");

            // Initialize default preferences if not provided
            if (user.getPreferences() == null) {
                user.setPreferences(createDefaultPreferences());
            }

            // Initialize activity tracking
            if (user.getActivity() == null) {
                user.setActivity(createDefaultActivity());
            }

            // Create the user entity (triggers UserInitializationProcessor)
            EntityWithMetadata<User> result = entityService.create(user);
            UUID technicalId = result.getId();

            logger.info("User created successfully with ID: {}", technicalId);
            return ResponseEntity.ok(technicalId);

        } catch (Exception e) {
            logger.error("Failed to create user: {}", user.getUsername(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user by business ID
     */
    @GetMapping("/{userId}")
    public ResponseEntity<EntityWithMetadata<User>> getUser(@PathVariable String userId) {
        logger.info("Retrieving user with ID: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            EntityWithMetadata<User> user = entityService.findByBusinessId(modelSpec, userId, "userId", User.class);

            if (user != null) {
                return ResponseEntity.ok(user);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve user with ID: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update user preferences (triggers UserPreferencesProcessor)
     */
    @PutMapping("/{userId}/preferences")
    public ResponseEntity<UUID> updateUserPreferences(@PathVariable String userId, 
                                                     @RequestBody User.UserPreferences preferences) {
        logger.info("Updating preferences for user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            
            // Get current user
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, "userId", User.class);
            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User user = userEntity.entity();
            user.setPreferences(preferences);
            user.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition to trigger processor
            EntityWithMetadata<User> result = entityService.updateByBusinessId(
                user, "userId", "update_preferences");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to update preferences for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Update user activity (triggers UserActivityProcessor)
     */
    @PutMapping("/{userId}/activity")
    public ResponseEntity<UUID> updateUserActivity(@PathVariable String userId, 
                                                  @RequestBody ActivityUpdateRequest activityRequest) {
        logger.info("Updating activity for user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            
            // Get current user
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, "userId", User.class);
            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User user = userEntity.entity();
            
            // Update activity based on request
            updateActivityFromRequest(user, activityRequest);
            user.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition to trigger processor
            EntityWithMetadata<User> result = entityService.updateByBusinessId(
                user, "userId", "update_activity");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to update activity for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Generate recommendations for user (triggers UserRecommendationProcessor)
     */
    @PostMapping("/{userId}/recommendations")
    public ResponseEntity<EntityWithMetadata<User>> generateRecommendations(@PathVariable String userId) {
        logger.info("Generating recommendations for user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            
            // Get current user
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, "userId", User.class);
            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User user = userEntity.entity();
            user.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition to trigger recommendation processor
            EntityWithMetadata<User> result = entityService.updateWithManualTransition(
                modelSpec, userId, user, "generate_recommendations");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Failed to generate recommendations for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user recommendations
     */
    @GetMapping("/{userId}/recommendations")
    public ResponseEntity<User.UserRecommendations> getUserRecommendations(@PathVariable String userId) {
        logger.info("Retrieving recommendations for user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, User.class);

            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User.UserRecommendations recommendations = userEntity.entity().getRecommendations();
            if (recommendations == null) {
                recommendations = new User.UserRecommendations();
            }

            return ResponseEntity.ok(recommendations);

        } catch (Exception e) {
            logger.error("Failed to retrieve recommendations for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get user activity summary
     */
    @GetMapping("/{userId}/activity")
    public ResponseEntity<User.UserActivity> getUserActivity(@PathVariable String userId) {
        logger.info("Retrieving activity for user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, User.class);

            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User.UserActivity activity = userEntity.entity().getActivity();
            if (activity == null) {
                activity = createDefaultActivity();
            }

            return ResponseEntity.ok(activity);

        } catch (Exception e) {
            logger.error("Failed to retrieve activity for user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Suspend user account
     */
    @PostMapping("/{userId}/suspend")
    public ResponseEntity<UUID> suspendUser(@PathVariable String userId) {
        logger.info("Suspending user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            
            // Get current user
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, User.class);
            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User user = userEntity.entity();
            user.setStatus("suspended");
            user.setUpdatedAt(LocalDateTime.now());

            // Update with manual transition
            EntityWithMetadata<User> result = entityService.updateWithManualTransition(
                modelSpec, userId, user, "suspend_user");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to suspend user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Reactivate user account
     */
    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<UUID> reactivateUser(@PathVariable String userId) {
        logger.info("Reactivating user: {}", userId);

        try {
            ModelSpec modelSpec = createUserModelSpec();
            
            // Get current user
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(modelSpec, userId, User.class);
            if (userEntity == null) {
                return ResponseEntity.notFound().build();
            }

            User user = userEntity.entity();
            user.setStatus("active");
            user.setUpdatedAt(LocalDateTime.now());
            user.setLastLoginAt(LocalDateTime.now());

            // Update with manual transition
            EntityWithMetadata<User> result = entityService.updateWithManualTransition(
                modelSpec, userId, user, "reactivate_user");

            return ResponseEntity.ok(result.getId());

        } catch (Exception e) {
            logger.error("Failed to reactivate user: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private ModelSpec createUserModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("User");
        modelSpec.setVersion(1);
        return modelSpec;
    }

    private User.UserPreferences createDefaultPreferences() {
        User.UserPreferences preferences = new User.UserPreferences();
        preferences.setSortPreference("relevance");
        preferences.setResultsPerPage(20);
        preferences.setEnableRecommendations(true);
        preferences.setEnableWeeklyReports(true);
        return preferences;
    }

    private User.UserActivity createDefaultActivity() {
        User.UserActivity activity = new User.UserActivity();
        activity.setTotalSearches(0);
        activity.setSearchesThisWeek(0);
        activity.setSearchesThisMonth(0);
        activity.setGenreSearchCounts(new HashMap<>());
        activity.setAuthorSearchCounts(new HashMap<>());
        return activity;
    }

    private void updateActivityFromRequest(User user, ActivityUpdateRequest request) {
        User.UserActivity activity = user.getActivity();
        if (activity == null) {
            activity = createDefaultActivity();
            user.setActivity(activity);
        }

        if (request.getSearchTerm() != null) {
            // This would typically be called when a user performs a search
            // The actual activity update logic is handled by the UserActivityProcessor
        }

        if (request.getViewedBookId() != null) {
            if (activity.getViewedBooks() == null) {
                activity.setViewedBooks(List.of(request.getViewedBookId()));
            }
        }

        if (request.getBookmarkedBookId() != null) {
            if (activity.getBookmarkedBooks() == null) {
                activity.setBookmarkedBooks(List.of(request.getBookmarkedBookId()));
            }
        }
    }

    /**
     * Request DTO for activity updates
     */
    @Getter
    @Setter
    public static class ActivityUpdateRequest {
        private String searchTerm;
        private String viewedBookId;
        private String bookmarkedBookId;
        private String genre;
        private String author;
    }
}
