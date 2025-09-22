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
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * UserActivityProcessor - Updates user activity tracking and search behavior
 * 
 * This processor handles:
 * - Updating search counts and activity metrics
 * - Tracking recent search terms and patterns
 * - Updating genre and author preferences based on activity
 * - Managing user engagement metrics
 */
@Component
public class UserActivityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserActivityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public UserActivityProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processUserActivity)
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

    private EntityWithMetadata<User> processUserActivity(EntityWithMetadata<User> entityWithMetadata) {
        User user = entityWithMetadata.getEntity();
        logger.info("Processing user activity for user: {}", user.getUserId());

        try {
            // Initialize activity if null
            if (user.getActivity() == null) {
                user.setActivity(new User.UserActivity());
            }

            // Update activity metrics
            updateActivityMetrics(user);

            // Update search patterns
            updateSearchPatterns(user);

            // Update preference scores based on activity
            updatePreferenceScores(user);

            // Update timestamps
            user.setUpdatedAt(LocalDateTime.now());
            user.getActivity().setLastSearchAt(LocalDateTime.now());

            logger.info("User activity updated for user: {}", user.getUserId());

        } catch (Exception e) {
            logger.error("Failed to process user activity for user: {}", user.getUserId(), e);
        }

        return entityWithMetadata;
    }

    private void updateActivityMetrics(User user) {
        User.UserActivity activity = user.getActivity();

        // Increment total searches
        Integer totalSearches = activity.getTotalSearches();
        activity.setTotalSearches(totalSearches != null ? totalSearches + 1 : 1);

        // Update weekly and monthly counts
        updatePeriodCounts(activity);

        logger.debug("Updated activity metrics for user: {} - Total searches: {}", 
                    user.getUserId(), activity.getTotalSearches());
    }

    private void updatePeriodCounts(User.UserActivity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSearch = activity.getLastSearchAt();

        // Update weekly count
        if (lastSearch == null || isWithinWeek(lastSearch, now)) {
            Integer weeklySearches = activity.getSearchesThisWeek();
            activity.setSearchesThisWeek(weeklySearches != null ? weeklySearches + 1 : 1);
        } else {
            activity.setSearchesThisWeek(1); // Reset if new week
        }

        // Update monthly count
        if (lastSearch == null || isWithinMonth(lastSearch, now)) {
            Integer monthlySearches = activity.getSearchesThisMonth();
            activity.setSearchesThisMonth(monthlySearches != null ? monthlySearches + 1 : 1);
        } else {
            activity.setSearchesThisMonth(1); // Reset if new month
        }
    }

    private boolean isWithinWeek(LocalDateTime lastSearch, LocalDateTime now) {
        return ChronoUnit.DAYS.between(lastSearch, now) < 7;
    }

    private boolean isWithinMonth(LocalDateTime lastSearch, LocalDateTime now) {
        return ChronoUnit.DAYS.between(lastSearch, now) < 30;
    }

    private void updateSearchPatterns(User user) {
        User.UserActivity activity = user.getActivity();

        // Update recent search terms (keep last 10)
        List<String> recentTerms = activity.getRecentSearchTerms();
        if (recentTerms == null) {
            recentTerms = new ArrayList<>();
            activity.setRecentSearchTerms(recentTerms);
        }

        // In a real implementation, you would get the actual search term from context
        // For now, we'll simulate adding a search term
        String simulatedSearchTerm = "recent_search_" + System.currentTimeMillis();
        recentTerms.add(0, simulatedSearchTerm); // Add to beginning

        // Keep only last 10 terms
        if (recentTerms.size() > 10) {
            recentTerms = recentTerms.subList(0, 10);
            activity.setRecentSearchTerms(recentTerms);
        }

        logger.debug("Updated search patterns for user: {} - Recent terms count: {}", 
                    user.getUserId(), recentTerms.size());
    }

    private void updatePreferenceScores(User user) {
        User.UserActivity activity = user.getActivity();

        // Update genre search counts
        updateGenreSearchCounts(activity);

        // Update author search counts
        updateAuthorSearchCounts(activity);

        // Update viewed and bookmarked books
        updateBookInteractions(activity);

        logger.debug("Updated preference scores for user: {}", user.getUserId());
    }

    private void updateGenreSearchCounts(User.UserActivity activity) {
        Map<String, Integer> genreCounts = activity.getGenreSearchCounts();
        if (genreCounts == null) {
            genreCounts = new HashMap<>();
            activity.setGenreSearchCounts(genreCounts);
        }

        // In a real implementation, you would get actual genres from the search context
        // For simulation, we'll increment some common genres
        List<String> simulatedGenres = Arrays.asList("Fiction", "Mystery", "Romance", "Science Fiction");
        String randomGenre = simulatedGenres.get(new Random().nextInt(simulatedGenres.size()));
        
        genreCounts.put(randomGenre, genreCounts.getOrDefault(randomGenre, 0) + 1);
    }

    private void updateAuthorSearchCounts(User.UserActivity activity) {
        Map<String, Integer> authorCounts = activity.getAuthorSearchCounts();
        if (authorCounts == null) {
            authorCounts = new HashMap<>();
            activity.setAuthorSearchCounts(authorCounts);
        }

        // In a real implementation, you would get actual authors from the search context
        // For simulation, we'll increment some authors
        List<String> simulatedAuthors = Arrays.asList("Stephen King", "Agatha Christie", "J.K. Rowling", "George Orwell");
        String randomAuthor = simulatedAuthors.get(new Random().nextInt(simulatedAuthors.size()));
        
        authorCounts.put(randomAuthor, authorCounts.getOrDefault(randomAuthor, 0) + 1);
    }

    private void updateBookInteractions(User.UserActivity activity) {
        // Update viewed books
        List<String> viewedBooks = activity.getViewedBooks();
        if (viewedBooks == null) {
            viewedBooks = new ArrayList<>();
            activity.setViewedBooks(viewedBooks);
        }

        // In a real implementation, you would get actual book IDs from context
        // For simulation, add a random book ID
        String simulatedBookId = "book_" + UUID.randomUUID().toString().substring(0, 8);
        if (!viewedBooks.contains(simulatedBookId)) {
            viewedBooks.add(simulatedBookId);
            
            // Keep only last 50 viewed books
            if (viewedBooks.size() > 50) {
                viewedBooks = viewedBooks.subList(viewedBooks.size() - 50, viewedBooks.size());
                activity.setViewedBooks(viewedBooks);
            }
        }

        // Update bookmarked books (less frequently)
        List<String> bookmarkedBooks = activity.getBookmarkedBooks();
        if (bookmarkedBooks == null) {
            bookmarkedBooks = new ArrayList<>();
            activity.setBookmarkedBooks(bookmarkedBooks);
        }

        // Simulate occasional bookmarking (10% chance)
        if (new Random().nextInt(10) == 0) {
            if (!bookmarkedBooks.contains(simulatedBookId)) {
                bookmarkedBooks.add(simulatedBookId);
            }
        }
    }
}
