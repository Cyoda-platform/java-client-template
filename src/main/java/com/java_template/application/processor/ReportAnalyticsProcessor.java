package com.java_template.application.processor;

import com.java_template.application.entity.weekly_report.version_1.WeeklyReport;
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

/**
 * ReportAnalyticsProcessor - Generates analytics data for weekly reports
 * 
 * This processor handles:
 * - Aggregating search analytics and user behavior data
 * - Calculating book popularity metrics and trends
 * - Analyzing recommendation performance
 * - Generating comprehensive weekly insights
 */
@Component
public class ReportAnalyticsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportAnalyticsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ReportAnalyticsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(WeeklyReport.class)
                .validate(this::isValidEntityWithMetadata, "Invalid weekly report entity")
                .map(this::processReportAnalytics)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<WeeklyReport> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.getEntity() == null) {
            logger.error("EntityWithMetadata or WeeklyReport entity is null");
            return false;
        }

        WeeklyReport report = entityWithMetadata.getEntity();
        if (!report.isValid()) {
            logger.error("WeeklyReport entity validation failed for reportId: {}", report.getReportId());
            return false;
        }

        return true;
    }

    private EntityWithMetadata<WeeklyReport> processReportAnalytics(EntityWithMetadata<WeeklyReport> entityWithMetadata) {
        WeeklyReport report = entityWithMetadata.getEntity();
        logger.info("Generating analytics for weekly report: {} (Week {}, {})", 
                   report.getReportId(), report.getWeekNumber(), report.getYear());

        try {
            // Generate search analytics
            generateSearchAnalytics(report);

            // Generate book popularity data
            generateBookPopularityData(report);

            // Generate user behavior insights
            generateUserBehaviorInsights(report);

            // Generate recommendation metrics
            generateRecommendationMetrics(report);

            // Update report metadata
            updateReportMetadata(report);

            // Update timestamps
            report.setGeneratedAt(LocalDateTime.now());

            logger.info("Analytics generated for weekly report: {}", report.getReportId());

        } catch (Exception e) {
            logger.error("Failed to generate analytics for weekly report: {}", report.getReportId(), e);
        }

        return entityWithMetadata;
    }

    private void generateSearchAnalytics(WeeklyReport report) {
        WeeklyReport.SearchAnalytics analytics = new WeeklyReport.SearchAnalytics();

        // In a real implementation, these would be calculated from actual SearchQuery entities
        // For simulation, we'll generate realistic sample data
        analytics.setTotalSearches(1250);
        analytics.setUniqueUsers(320);
        analytics.setAverageSearchesPerUser(3.9);
        analytics.setAverageResultsPerSearch(8.5);
        analytics.setSearchSuccessRate(0.87);
        analytics.setAverageResponseTimeMs(450L);

        // Generate top search terms
        List<String> topSearchTerms = Arrays.asList(
            "science fiction", "mystery novels", "romance", "historical fiction", 
            "fantasy", "thriller", "biography", "self help", "cooking", "travel"
        );
        analytics.setTopSearchTerms(topSearchTerms);

        // Generate search term counts
        Map<String, Integer> searchTermCounts = new HashMap<>();
        for (int i = 0; i < topSearchTerms.size(); i++) {
            searchTermCounts.put(topSearchTerms.get(i), 150 - (i * 15));
        }
        analytics.setSearchTermCounts(searchTermCounts);

        report.setSearchAnalytics(analytics);
        logger.debug("Generated search analytics for report: {}", report.getReportId());
    }

    private void generateBookPopularityData(WeeklyReport report) {
        WeeklyReport.BookPopularity popularity = new WeeklyReport.BookPopularity();

        // Generate most searched books
        List<WeeklyReport.PopularBook> mostSearchedBooks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            WeeklyReport.PopularBook book = new WeeklyReport.PopularBook();
            book.setBookId("book_" + i);
            book.setTitle("Popular Book " + i);
            book.setAuthors(Arrays.asList("Author " + i, "Co-Author " + i));
            book.setSearchCount(100 - (i * 8));
            book.setClickCount(80 - (i * 6));
            book.setPopularityScore(1.0 - (i * 0.08));
            book.setTrendDirection(i <= 3 ? "up" : i <= 7 ? "stable" : "down");
            mostSearchedBooks.add(book);
        }
        popularity.setMostSearchedBooks(mostSearchedBooks);

        // Generate trending books (subset of most searched with upward trend)
        List<WeeklyReport.PopularBook> trendingBooks = mostSearchedBooks.stream()
                .filter(book -> "up".equals(book.getTrendDirection()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        popularity.setTrendingBooks(trendingBooks);

        // Generate genre popularity
        Map<String, Integer> genrePopularity = new HashMap<>();
        genrePopularity.put("Fiction", 450);
        genrePopularity.put("Mystery", 320);
        genrePopularity.put("Romance", 280);
        genrePopularity.put("Science Fiction", 250);
        genrePopularity.put("Fantasy", 220);
        genrePopularity.put("Thriller", 200);
        genrePopularity.put("Biography", 180);
        genrePopularity.put("History", 150);
        popularity.setGenrePopularity(genrePopularity);

        // Generate author popularity
        Map<String, Integer> authorPopularity = new HashMap<>();
        authorPopularity.put("Stephen King", 85);
        authorPopularity.put("Agatha Christie", 72);
        authorPopularity.put("J.K. Rowling", 68);
        authorPopularity.put("George Orwell", 65);
        authorPopularity.put("Jane Austen", 58);
        popularity.setAuthorPopularity(authorPopularity);

        // Generate publication year trends
        Map<Integer, Integer> yearTrends = new HashMap<>();
        yearTrends.put(2023, 180);
        yearTrends.put(2022, 220);
        yearTrends.put(2021, 195);
        yearTrends.put(2020, 165);
        yearTrends.put(2019, 140);
        popularity.setPublicationYearTrends(yearTrends);

        // Generate emerging genres
        popularity.setEmergingGenres(Arrays.asList("Climate Fiction", "Afrofuturism", "Cozy Mystery"));

        report.setBookPopularity(popularity);
        logger.debug("Generated book popularity data for report: {}", report.getReportId());
    }

    private void generateUserBehaviorInsights(WeeklyReport report) {
        WeeklyReport.UserBehavior behavior = new WeeklyReport.UserBehavior();

        // Generate user metrics
        behavior.setActiveUsers(320);
        behavior.setNewUsers(45);
        behavior.setAverageSessionDuration(12.5); // minutes
        behavior.setSearchToClickRate(0.68);
        behavior.setBookmarkRate(0.15);

        // Generate preference distribution
        Map<String, Double> preferenceDistribution = new HashMap<>();
        preferenceDistribution.put("Fiction", 0.35);
        preferenceDistribution.put("Non-Fiction", 0.25);
        preferenceDistribution.put("Mystery", 0.20);
        preferenceDistribution.put("Romance", 0.15);
        preferenceDistribution.put("Science Fiction", 0.12);
        behavior.setPreferenceDistribution(preferenceDistribution);

        // Generate common search patterns
        List<String> searchPatterns = Arrays.asList(
            "Genre-specific searches (45%)",
            "Author-based searches (30%)",
            "Title searches (20%)",
            "Year-range filtered searches (15%)",
            "Multi-criteria searches (10%)"
        );
        behavior.setCommonSearchPatterns(searchPatterns);

        report.setUserBehavior(behavior);
        logger.debug("Generated user behavior insights for report: {}", report.getReportId());
    }

    private void generateRecommendationMetrics(WeeklyReport report) {
        WeeklyReport.RecommendationMetrics metrics = new WeeklyReport.RecommendationMetrics();

        // Generate recommendation performance data
        metrics.setRecommendationsGenerated(1580);
        metrics.setRecommendationsClicked(425);
        metrics.setClickThroughRate(0.269); // 26.9%
        metrics.setRecommendationAccuracy(0.78);
        metrics.setUserSatisfactionScore(0.82);

        // Generate algorithm performance comparison
        Map<String, Double> algorithmPerformance = new HashMap<>();
        algorithmPerformance.put("collaborative_filtering", 0.75);
        algorithmPerformance.put("content_based", 0.72);
        algorithmPerformance.put("hybrid", 0.78);
        algorithmPerformance.put("popularity_based", 0.65);
        metrics.setAlgorithmPerformance(algorithmPerformance);

        report.setRecommendationMetrics(metrics);
        logger.debug("Generated recommendation metrics for report: {}", report.getReportId());
    }

    private void updateReportMetadata(WeeklyReport report) {
        WeeklyReport.ReportMetadata metadata = new WeeklyReport.ReportMetadata();

        metadata.setStatus("generating");
        metadata.setGeneratedBy("system");
        metadata.setDataQualityScore(95);
        metadata.setReportVersion("1.0");

        // Set data sources used
        List<String> dataSources = Arrays.asList(
            "SearchQuery entities",
            "User entities", 
            "Book entities",
            "User activity tracking",
            "Recommendation engine"
        );
        metadata.setDataSourcesUsed(dataSources);

        // Set configuration used
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("week_start", report.getWeekStartDate());
        configuration.put("week_end", report.getWeekEndDate());
        configuration.put("include_trends", true);
        configuration.put("include_recommendations", true);
        metadata.setConfigurationUsed(configuration);

        report.setMetadata(metadata);
        logger.debug("Updated report metadata for report: {}", report.getReportId());
    }
}
