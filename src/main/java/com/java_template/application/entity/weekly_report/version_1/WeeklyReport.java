package com.java_template.application.entity.weekly_report.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WeeklyReport Entity - Represents weekly analytics and reporting data
 * 
 * This entity stores weekly report information including:
 * - Most searched books and trends
 * - User activity statistics
 * - Popular genres and authors
 * - Recommendation performance metrics
 */
@Data
public class WeeklyReport implements CyodaEntity {
    public static final String ENTITY_NAME = WeeklyReport.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String reportId;
    
    // Report period
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private Integer weekNumber;
    private Integer year;
    
    // Search analytics
    private SearchAnalytics searchAnalytics;
    
    // Book popularity data
    private BookPopularity bookPopularity;
    
    // User behavior insights
    private UserBehavior userBehavior;
    
    // Recommendation performance
    private RecommendationMetrics recommendationMetrics;
    
    // Report metadata
    private ReportMetadata metadata;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime generatedAt;
    private LocalDateTime publishedAt;

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
        return reportId != null && !reportId.trim().isEmpty() &&
               weekStartDate != null && weekEndDate != null &&
               weekNumber != null && year != null;
    }

    /**
     * Nested class for search analytics
     */
    @Data
    public static class SearchAnalytics {
        private Integer totalSearches;
        private Integer uniqueUsers;
        private Double averageSearchesPerUser;
        private List<String> topSearchTerms;
        private Map<String, Integer> searchTermCounts;
        private Double averageResultsPerSearch;
        private Double searchSuccessRate;
        private Long averageResponseTimeMs;
    }

    /**
     * Nested class for book popularity data
     */
    @Data
    public static class BookPopularity {
        private List<PopularBook> mostSearchedBooks;
        private List<PopularBook> trendingBooks;
        private Map<String, Integer> genrePopularity;
        private Map<String, Integer> authorPopularity;
        private Map<Integer, Integer> publicationYearTrends;
        private List<String> emergingGenres;
    }

    /**
     * Nested class for individual popular book data
     */
    @Data
    public static class PopularBook {
        private String bookId;
        private String title;
        private List<String> authors;
        private Integer searchCount;
        private Integer clickCount;
        private Double popularityScore;
        private String trendDirection; // "up", "down", "stable", "new"
    }

    /**
     * Nested class for user behavior insights
     */
    @Data
    public static class UserBehavior {
        private Integer activeUsers;
        private Integer newUsers;
        private Double averageSessionDuration;
        private Double searchToClickRate;
        private Double bookmarkRate;
        private Map<String, Double> preferenceDistribution;
        private List<String> commonSearchPatterns;
    }

    /**
     * Nested class for recommendation performance metrics
     */
    @Data
    public static class RecommendationMetrics {
        private Integer recommendationsGenerated;
        private Integer recommendationsClicked;
        private Double clickThroughRate;
        private Double recommendationAccuracy;
        private Map<String, Double> algorithmPerformance;
        private Double userSatisfactionScore;
    }

    /**
     * Nested class for report metadata
     */
    @Data
    public static class ReportMetadata {
        private String status; // "generating", "completed", "published", "archived"
        private String generatedBy; // "system", "manual"
        private Long generationTimeMs;
        private Integer dataQualityScore;
        private List<String> dataSourcesUsed;
        private String reportVersion;
        private Map<String, Object> configurationUsed;
    }
}
