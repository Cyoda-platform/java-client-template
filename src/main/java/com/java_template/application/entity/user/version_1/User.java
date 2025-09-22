package com.java_template.application.entity.user.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * User Entity - Represents a user of the book search application
 * 
 * This entity stores user information including:
 * - Basic user profile
 * - Search preferences and history
 * - Recommendation settings
 * - Activity tracking for personalization
 */
@Data
public class User implements CyodaEntity {
    public static final String ENTITY_NAME = User.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String userId;
    
    // Basic user information
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    
    // User preferences
    private UserPreferences preferences;
    
    // Search and activity tracking
    private UserActivity activity;
    
    // Recommendation data
    private UserRecommendations recommendations;
    
    // Account status
    private String status; // "active", "inactive", "suspended"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return userId != null && !userId.trim().isEmpty() &&
               username != null && !username.trim().isEmpty() &&
               email != null && !email.trim().isEmpty();
    }

    /**
     * Nested class for user preferences
     */
    @Data
    public static class UserPreferences {
        private List<String> favoriteGenres;
        private List<String> favoriteAuthors;
        private Integer preferredPublicationYearStart;
        private Integer preferredPublicationYearEnd;
        private List<String> preferredLanguages;
        private String sortPreference; // "relevance", "title", "author", "year"
        private Integer resultsPerPage;
        private Boolean enableRecommendations;
        private Boolean enableWeeklyReports;
    }

    /**
     * Nested class for user activity tracking
     */
    @Data
    public static class UserActivity {
        private Integer totalSearches;
        private Integer searchesThisWeek;
        private Integer searchesThisMonth;
        private LocalDateTime lastSearchAt;
        private List<String> recentSearchTerms;
        private Map<String, Integer> genreSearchCounts;
        private Map<String, Integer> authorSearchCounts;
        private List<String> viewedBooks;
        private List<String> bookmarkedBooks;
    }

    /**
     * Nested class for user recommendations
     */
    @Data
    public static class UserRecommendations {
        private List<String> recommendedBooks;
        private LocalDateTime lastRecommendationUpdate;
        private String recommendationAlgorithm; // "collaborative", "content_based", "hybrid"
        private Double recommendationScore;
        private Map<String, Double> genreAffinityScores;
        private Map<String, Double> authorAffinityScores;
    }
}
