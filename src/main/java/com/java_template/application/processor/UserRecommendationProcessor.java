package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UserRecommendationProcessor - Generates personalized book recommendations
 * 
 * This processor handles:
 * - Analyzing user preferences and search history
 * - Calculating genre and author affinity scores
 * - Generating personalized book recommendations
 * - Updating recommendation metadata and scores
 */
@Component
public class UserRecommendationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserRecommendationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserRecommendationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(User.class)
                .validate(this::isValidEntityWithMetadata, "Invalid user entity")
                .map(this::processUserRecommendations)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.getEntity() == null) {
            logger.error("EntityWithMetadata or User entity is null");
            return false;
        }

        User user = entityWithMetadata.getEntity();
        if (!user.isValid()) {
            logger.error("User entity validation failed for userId: {}", user.getUserId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<User> processUserRecommendations(EntityWithMetadata<User> entityWithMetadata) {
        User user = entityWithMetadata.getEntity();
        logger.info("Generating recommendations for user: {}", user.getUserId());

        try {
            // Initialize recommendations if null
            if (user.getRecommendations() == null) {
                user.setRecommendations(new User.UserRecommendations());
            }

            // Calculate affinity scores
            calculateAffinityScores(user);

            // Generate recommendations based on preferences and activity
            generateRecommendations(user);

            // Update recommendation metadata
            updateRecommendationMetadata(user);

            // Update user timestamp
            user.setUpdatedAt(LocalDateTime.now());

            logger.info("Recommendations generated for user: {} - {} recommendations", 
                       user.getUserId(), user.getRecommendations().getRecommendedBooks().size());

        } catch (Exception e) {
            logger.error("Failed to generate recommendations for user: {}", user.getUserId(), e);
        }

        return entityWithMetadata;
    }

    private void calculateAffinityScores(User user) {
        User.UserRecommendations recommendations = user.getRecommendations();
        User.UserActivity activity = user.getActivity();
        User.UserPreferences preferences = user.getPreferences();

        // Calculate genre affinity scores
        Map<String, Double> genreAffinityScores = calculateGenreAffinityScores(activity, preferences);
        recommendations.setGenreAffinityScores(genreAffinityScores);

        // Calculate author affinity scores
        Map<String, Double> authorAffinityScores = calculateAuthorAffinityScores(activity, preferences);
        recommendations.setAuthorAffinityScores(authorAffinityScores);

        logger.debug("Calculated affinity scores for user: {} - Genres: {}, Authors: {}", 
                    user.getUserId(), genreAffinityScores.size(), authorAffinityScores.size());
    }

    private Map<String, Double> calculateGenreAffinityScores(User.UserActivity activity, User.UserPreferences preferences) {
        Map<String, Double> affinityScores = new HashMap<>();

        // Base scores from explicit preferences
        if (preferences != null && preferences.getFavoriteGenres() != null) {
            for (String genre : preferences.getFavoriteGenres()) {
                affinityScores.put(genre, 0.8); // High base score for explicit preferences
            }
        }

        // Enhance scores based on search activity
        if (activity != null && activity.getGenreSearchCounts() != null) {
            Map<String, Integer> genreCounts = activity.getGenreSearchCounts();
            int totalSearches = genreCounts.values().stream().mapToInt(Integer::intValue).sum();

            if (totalSearches > 0) {
                for (Map.Entry<String, Integer> entry : genreCounts.entrySet()) {
                    String genre = entry.getKey();
                    double frequency = (double) entry.getValue() / totalSearches;
                    double activityScore = Math.min(frequency * 2.0, 1.0); // Cap at 1.0

                    // Combine with existing score or set new score
                    double existingScore = affinityScores.getOrDefault(genre, 0.0);
                    double combinedScore = Math.max(existingScore, activityScore);
                    affinityScores.put(genre, combinedScore);
                }
            }
        }

        return affinityScores;
    }

    private Map<String, Double> calculateAuthorAffinityScores(User.UserActivity activity, User.UserPreferences preferences) {
        Map<String, Double> affinityScores = new HashMap<>();

        // Base scores from explicit preferences
        if (preferences != null && preferences.getFavoriteAuthors() != null) {
            for (String author : preferences.getFavoriteAuthors()) {
                affinityScores.put(author, 0.8); // High base score for explicit preferences
            }
        }

        // Enhance scores based on search activity
        if (activity != null && activity.getAuthorSearchCounts() != null) {
            Map<String, Integer> authorCounts = activity.getAuthorSearchCounts();
            int totalSearches = authorCounts.values().stream().mapToInt(Integer::intValue).sum();

            if (totalSearches > 0) {
                for (Map.Entry<String, Integer> entry : authorCounts.entrySet()) {
                    String author = entry.getKey();
                    double frequency = (double) entry.getValue() / totalSearches;
                    double activityScore = Math.min(frequency * 2.0, 1.0); // Cap at 1.0

                    // Combine with existing score or set new score
                    double existingScore = affinityScores.getOrDefault(author, 0.0);
                    double combinedScore = Math.max(existingScore, activityScore);
                    affinityScores.put(author, combinedScore);
                }
            }
        }

        return affinityScores;
    }

    private void generateRecommendations(User user) {
        User.UserRecommendations recommendations = user.getRecommendations();
        
        // Get top genres and authors by affinity score
        List<String> topGenres = getTopAffinityItems(recommendations.getGenreAffinityScores(), 3);
        List<String> topAuthors = getTopAffinityItems(recommendations.getAuthorAffinityScores(), 3);

        // Generate book recommendations based on affinities
        List<String> recommendedBooks = generateBookRecommendations(topGenres, topAuthors, user);
        recommendations.setRecommendedBooks(recommendedBooks);

        // Calculate overall recommendation score
        double recommendationScore = calculateOverallRecommendationScore(user);
        recommendations.setRecommendationScore(recommendationScore);

        logger.debug("Generated {} recommendations for user: {} with score: {}", 
                    recommendedBooks.size(), user.getUserId(), recommendationScore);
    }

    private List<String> getTopAffinityItems(Map<String, Double> affinityScores, int limit) {
        if (affinityScores == null || affinityScores.isEmpty()) {
            return new ArrayList<>();
        }

        return affinityScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> generateBookRecommendations(List<String> topGenres, List<String> topAuthors, User user) {
        List<String> recommendations = new ArrayList<>();

        // In a real implementation, this would query the Book entities
        // based on the user's top genres and authors
        // For simulation, we'll generate sample book IDs

        // Generate recommendations based on top genres
        for (String genre : topGenres) {
            for (int i = 1; i <= 2; i++) {
                String bookId = "book_" + genre.toLowerCase().replace(" ", "_") + "_" + i;
                recommendations.add(bookId);
            }
        }

        // Generate recommendations based on top authors
        for (String author : topAuthors) {
            for (int i = 1; i <= 2; i++) {
                String bookId = "book_" + author.toLowerCase().replace(" ", "_") + "_" + i;
                recommendations.add(bookId);
            }
        }

        // Remove duplicates and limit to 10 recommendations
        recommendations = recommendations.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return recommendations;
    }

    private double calculateOverallRecommendationScore(User user) {
        User.UserActivity activity = user.getActivity();
        User.UserRecommendations recommendations = user.getRecommendations();

        double score = 0.5; // Base score

        // Increase score based on user activity
        if (activity != null) {
            Integer totalSearches = activity.getTotalSearches();
            if (totalSearches != null && totalSearches > 0) {
                score += Math.min(totalSearches * 0.01, 0.3); // Up to 0.3 bonus for activity
            }
        }

        // Increase score based on preference diversity
        if (recommendations != null) {
            int genreCount = recommendations.getGenreAffinityScores() != null ? 
                           recommendations.getGenreAffinityScores().size() : 0;
            int authorCount = recommendations.getAuthorAffinityScores() != null ? 
                            recommendations.getAuthorAffinityScores().size() : 0;
            
            score += Math.min((genreCount + authorCount) * 0.02, 0.2); // Up to 0.2 bonus for diversity
        }

        return Math.min(score, 1.0); // Cap at 1.0
    }

    private void updateRecommendationMetadata(User user) {
        User.UserRecommendations recommendations = user.getRecommendations();
        
        recommendations.setLastRecommendationUpdate(LocalDateTime.now());
        recommendations.setRecommendationAlgorithm("hybrid"); // Content-based + collaborative filtering
    }
}
