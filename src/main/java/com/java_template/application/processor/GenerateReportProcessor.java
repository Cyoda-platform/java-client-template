package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class GenerateReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GenerateReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GenerateReportProcessor(SerializerFactory serializerFactory,
                                   EntityService entityService,
                                   ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeeklyJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyJob> context) {
        WeeklyJob job = context.entity();

        // Determine reporting window. If lastRunAt/nextRunAt are not provided, default to last 7 days.
        String periodStart;
        String periodEnd;
        DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

        if (job.getLastRunAt() != null && !job.getLastRunAt().isBlank()) {
            periodStart = job.getLastRunAt();
        } else {
            periodStart = isoFormatter.format(Instant.now().minusSeconds(7 * 24 * 3600));
        }

        if (job.getNextRunAt() != null && !job.getNextRunAt().isBlank()) {
            periodEnd = job.getNextRunAt();
        } else {
            periodEnd = isoFormatter.format(Instant.now());
        }

        // Query Book entities fetched within the reporting window using fetchTimestamp between periodStart and periodEnd
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.fetchTimestamp", "GREATER_THAN", periodStart),
            Condition.of("$.fetchTimestamp", "LESS_THAN", periodEnd)
        );

        List<Book> books = new ArrayList<>();
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Book.ENTITY_NAME,
                String.valueOf(Book.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode nodes = itemsFuture.join();
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    try {
                        Book b = objectMapper.convertValue(nodes.get(i), Book.class);
                        if (b != null && b.isValid()) {
                            books.add(b);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to convert book node to Book.class: {}", ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error fetching books for report window {} - {} : {}", periodStart, periodEnd, ex.getMessage());
            // proceed with empty books list
        }

        // Aggregate metrics
        int totalBooks = books.size();
        int totalPageCount = books.stream()
            .map(Book::getPageCount)
            .filter(p -> p != null)
            .mapToInt(Integer::intValue)
            .sum();

        // Select top 5 popular titles by popularityScore (fall back to pageCount)
        List<Book> sorted = books.stream()
            .sorted(Comparator.comparingDouble((Book b) -> {
                Double score = b.getPopularityScore();
                if (score != null) return -score;
                Integer pc = b.getPageCount();
                return pc != null ? -pc.doubleValue() : 0.0;
            }))
            .limit(5)
            .collect(Collectors.toList());

        List<Report.BookSummary> popularSummaries = new ArrayList<>();
        for (Book b : sorted) {
            Report.BookSummary bs = new Report.BookSummary();
            bs.setTitle(b.getTitle());
            bs.setDescription(b.getDescription());
            bs.setExcerpt(b.getExcerpt());
            bs.setPageCount(b.getPageCount());
            bs.setPublishDate(b.getPublishDate());
            // ensure validity by not adding invalid summaries
            if (bs.isValid()) {
                popularSummaries.add(bs);
            }
        }

        // Generate simple titleInsights
        String titleInsights;
        if (totalBooks == 0) {
            titleInsights = "No books found in the reporting window.";
        } else {
            double avgPages = totalBooks > 0 ? (double) totalPageCount / totalBooks : 0.0;
            titleInsights = String.format("Analyzed %d titles; average page count %.1f.", totalBooks, avgPages);
        }

        // Create Report entity
        Report report = new Report();
        String generatedAt = isoFormatter.format(Instant.now());
        String reportId = "report_" + DateTimeFormatter.ofPattern("yyyy_MM_dd").withZone(ZoneOffset.UTC).format(Instant.now()) + "_" + UUID.randomUUID().toString().substring(0, 8);

        report.setReportId(reportId);
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setGeneratedAt(generatedAt);
        report.setTotalBooks(totalBooks);
        report.setTotalPageCount(totalPageCount);
        report.setTitleInsights(titleInsights);
        report.setPopularTitles(popularSummaries);
        report.setFormat("inline");
        report.setStatus("GENERATED");
        // sentAt left null until SendReportProcessor runs

        // Persist report (add new entity). Do not update the triggering WeeklyJob via entityService.
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Report.ENTITY_NAME,
                String.valueOf(Report.ENTITY_VERSION),
                report
            );
            UUID createdId = idFuture.join();
            logger.info("Created Report entity with technical id: {}", createdId);
        } catch (Exception ex) {
            logger.error("Failed to persist Report entity: {}", ex.getMessage());
            // mark job as FAILED if report couldn't be created
            job.setStatus("FAILED");
            return job;
        }

        // Update job state to COMPLETED and set lastRunAt to generatedAt
        job.setStatus("COMPLETED");
        job.setLastRunAt(generatedAt);

        return job;
    }
}