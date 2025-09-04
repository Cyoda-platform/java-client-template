package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReportGenerationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportGenerationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReportGenerationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report generation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Report entity) {
        return entity != null && entity.isValid();
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report report = context.entity();

        try {
            // Get all analyzed books for the reporting period
            List<EntityResponse<Book>> bookResponses = getBooksForReportingPeriod(report);
            List<Book> analyzedBooks = bookResponses.stream()
                .map(EntityResponse::getData)
                .filter(book -> book.getAnalysisScore() != null)
                .collect(Collectors.toList());

            logger.info("Found {} analyzed books for report period", analyzedBooks.size());

            // Calculate aggregate metrics
            report.setTotalBooksAnalyzed(analyzedBooks.size());
            report.setTotalPageCount(analyzedBooks.stream()
                .mapToLong(book -> book.getPageCount() != null ? book.getPageCount() : 0)
                .sum());
            report.setAveragePageCount(report.getTotalBooksAnalyzed() > 0 ? 
                (double) report.getTotalPageCount() / report.getTotalBooksAnalyzed() : 0.0);

            // Identify popular titles (top 10 by analysis score)
            List<Book> topBooks = analyzedBooks.stream()
                .sorted((b1, b2) -> Double.compare(
                    b2.getAnalysisScore() != null ? b2.getAnalysisScore() : 0.0,
                    b1.getAnalysisScore() != null ? b1.getAnalysisScore() : 0.0))
                .limit(10)
                .collect(Collectors.toList());

            report.setPopularTitles(generatePopularTitlesJson(topBooks));

            // Analyze publication dates
            report.setPublicationDateInsights(generatePublicationInsightsJson(analyzedBooks));

            // Generate summary text
            report.setReportSummary(generateSummaryText(report));
            report.setGeneratedAt(LocalDateTime.now());

            // Update book entities to reference this report
            updateBooksWithReportReference(bookResponses, report);

            logger.info("Report generated: {}", report.getReportId());
        } catch (Exception e) {
            logger.error("Failed to generate report {}: {}", report.getReportId(), e.getMessage(), e);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }

        return report;
    }

    private List<EntityResponse<Book>> getBooksForReportingPeriod(Report report) {
        // Search for books retrieved during the reporting period
        Condition startCondition = Condition.of("$.retrievedAt", "GREATER_THAN_OR_EQUAL", 
            report.getReportPeriodStart().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Condition endCondition = Condition.of("$.retrievedAt", "LESS_THAN_OR_EQUAL", 
            report.getReportPeriodEnd().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        SearchConditionRequest condition = SearchConditionRequest.group("AND", startCondition, endCondition);
        
        return entityService.search(Book.class, condition);
    }

    private String generatePopularTitlesJson(List<Book> topBooks) {
        try {
            List<Map<String, Object>> popularTitles = topBooks.stream()
                .map(book -> {
                    Map<String, Object> titleInfo = new HashMap<>();
                    titleInfo.put("title", book.getTitle());
                    titleInfo.put("score", book.getAnalysisScore());
                    titleInfo.put("pageCount", book.getPageCount());
                    return titleInfo;
                })
                .collect(Collectors.toList());
            
            return objectMapper.writeValueAsString(popularTitles);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize popular titles: {}", e.getMessage());
            return "[]";
        }
    }

    private String generatePublicationInsightsJson(List<Book> books) {
        try {
            Map<Integer, Long> yearCounts = books.stream()
                .filter(book -> book.getPublishDate() != null)
                .collect(Collectors.groupingBy(
                    book -> book.getPublishDate().getYear(),
                    Collectors.counting()));

            if (yearCounts.isEmpty()) {
                return "{}";
            }

            int minYear = yearCounts.keySet().stream().min(Integer::compareTo).orElse(0);
            int maxYear = yearCounts.keySet().stream().max(Integer::compareTo).orElse(0);
            Map.Entry<Integer, Long> mostProductiveYear = yearCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElse(null);

            Map<String, Object> insights = new HashMap<>();
            insights.put("totalYearsSpanned", maxYear - minYear);
            insights.put("mostProductiveYear", mostProductiveYear != null ? mostProductiveYear.getKey() : 0);
            insights.put("booksInMostProductiveYear", mostProductiveYear != null ? mostProductiveYear.getValue() : 0);
            insights.put("averageBooksPerYear", yearCounts.size() > 0 ? 
                (double) books.size() / yearCounts.size() : 0.0);

            return objectMapper.writeValueAsString(insights);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize publication insights: {}", e.getMessage());
            return "{}";
        }
    }

    private String generateSummaryText(Report report) {
        StringBuilder summary = new StringBuilder();
        summary.append("Weekly Book Analytics Report\n");
        summary.append("Generated on: ").append(report.getGeneratedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        summary.append("Period: ").append(report.getReportPeriodStart().format(DateTimeFormatter.ISO_LOCAL_DATE))
               .append(" to ").append(report.getReportPeriodEnd().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n");
        summary.append("Key Insights:\n");
        summary.append("- Total books analyzed: ").append(report.getTotalBooksAnalyzed()).append("\n");
        summary.append("- Total pages: ").append(report.getTotalPageCount()).append("\n");
        summary.append("- Average pages per book: ").append(String.format("%.2f", report.getAveragePageCount())).append("\n");
        summary.append("- Top performing titles included in popular titles section\n");
        
        return summary.toString();
    }

    private void updateBooksWithReportReference(List<EntityResponse<Book>> bookResponses, Report report) {
        // Note: We can't get the report's technical ID here since it's not saved yet
        // This would typically be done in a separate processor or after the report is saved
        logger.info("Would update {} books with report reference (implementation depends on report ID availability)", 
            bookResponses.size());
    }
}
