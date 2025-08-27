package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class ValidateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidateReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
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

        // Ensure required period boundaries exist for any data enrichment
        if (report.getPeriodStart() == null || report.getPeriodStart().isBlank()
            || report.getPeriodEnd() == null || report.getPeriodEnd().isBlank()) {
            logger.warn("Report missing periodStart or periodEnd - marking as FAILED");
            report.setStatus("FAILED");
            return report;
        }

        // If totals are missing or invalid, attempt to compute them from Book entities
        boolean totalsMissing = report.getTotalBooks() == null || report.getTotalBooks() < 0
            || report.getTotalPageCount() == null || report.getTotalPageCount() < 0;

        try {
            ArrayNode books = null;
            if (totalsMissing || report.getPopularTitles() == null || report.getPopularTitles().isEmpty()) {
                // Build simple condition: publishDate between periodStart and periodEnd (inclusive)
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.publishDate", "GREATER_THAN_EQUAL", report.getPeriodStart()),
                    Condition.of("$.publishDate", "LESS_THAN_EQUAL", report.getPeriodEnd())
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Book.ENTITY_NAME,
                    String.valueOf(Book.ENTITY_VERSION),
                    condition,
                    true
                );
                books = itemsFuture.join(); // blocking join - required for processor synchronous logic
            }

            // Compute totals if missing
            if (totalsMissing) {
                int totalBooks = 0;
                int totalPageCount = 0;
                if (books != null) {
                    for (JsonNode b : books) {
                        totalBooks++;
                        if (b.has("pageCount") && !b.get("pageCount").isNull()) {
                            totalPageCount += b.get("pageCount").asInt(0);
                        }
                    }
                }
                report.setTotalBooks(totalBooks);
                report.setTotalPageCount(totalPageCount);
                logger.debug("Computed totals: totalBooks={}, totalPageCount={}", totalBooks, totalPageCount);
            }

            // Ensure popularTitles present; if not, attempt to create top-N (5) from fetched books by popularityScore
            if (report.getPopularTitles() == null || report.getPopularTitles().isEmpty()) {
                List<Report.BookSummary> summaries = new ArrayList<>();
                if (books != null && books.size() > 0) {
                    List<JsonNode> sorted = new ArrayList<>();
                    books.forEach(sorted::add);
                    // sort descending by popularityScore (missing treated as 0.0)
                    sorted = sorted.stream()
                        .sorted(Comparator.comparingDouble((JsonNode n) -> {
                            if (n.has("popularityScore") && !n.get("popularityScore").isNull()) {
                                return -n.get("popularityScore").asDouble(0.0);
                            }
                            return 0.0;
                        }))
                        .limit(5)
                        .collect(Collectors.toList());

                    for (JsonNode b : sorted) {
                        Report.BookSummary bs = new Report.BookSummary();
                        if (b.has("title") && !b.get("title").isNull()) bs.setTitle(b.get("title").asText());
                        if (b.has("description") && !b.get("description").isNull()) bs.setDescription(b.get("description").asText());
                        if (b.has("excerpt") && !b.get("excerpt").isNull()) bs.setExcerpt(b.get("excerpt").asText());
                        if (b.has("pageCount") && !b.get("pageCount").isNull()) bs.setPageCount(b.get("pageCount").asInt());
                        if (b.has("publishDate") && !b.get("publishDate").isNull()) bs.setPublishDate(b.get("publishDate").asText());
                        summaries.add(bs);
                    }
                    report.setPopularTitles(summaries);
                    logger.debug("Built popularTitles from books (count={})", summaries.size());
                } else {
                    logger.warn("No books found to derive popularTitles");
                }
            }

            // Final validation of popularTitles entries
            if (report.getPopularTitles() == null || report.getPopularTitles().isEmpty()) {
                logger.warn("Report popularTitles missing after attempt to derive - marking as FAILED");
                report.setStatus("FAILED");
                return report;
            } else {
                for (Report.BookSummary bs : report.getPopularTitles()) {
                    if (bs == null || !bs.isValid()) {
                        logger.warn("Invalid BookSummary found in popularTitles - marking report as FAILED");
                        report.setStatus("FAILED");
                        return report;
                    }
                }
            }

            // All checks passed -> mark as VALIDATED
            report.setStatus("VALIDATED");
            logger.info("Report validated successfully: reportId={}", report.getReportId());
            return report;

        } catch (Exception ex) {
            logger.error("Error while validating report: {}", ex.getMessage(), ex);
            report.setStatus("FAILED");
            return report;
        }
    }
}