package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.book.version_1.Book;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class AnalyzeMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AnalyzeMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyzeMetrics for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(FetchJob.class)
                .validate(this::isValidEntity, "Invalid FetchJob state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob job) {
        return job != null && job.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob job = context.entity();
        try {
            OffsetDateTime rangeStart = job.getLastRunAt();
            OffsetDateTime rangeEnd = OffsetDateTime.now(ZoneOffset.UTC);

            boolean snapshot = false;
            if (job.getParameters() != null && job.getParameters().containsKey("reportRange")) {
                Object v = job.getParameters().get("reportRange");
                if (v != null && "snapshot".equalsIgnoreCase(String.valueOf(v))) snapshot = true;
            }

            ArrayNode booksArray;
            if (snapshot || rangeStart == null) {
                // query all books
                CompletableFuture<ArrayNode> fut = entityService.getItems(Book.ENTITY_NAME, String.valueOf(Book.ENTITY_VERSION));
                booksArray = fut.get();
            } else {
                // delta: retrievedAt > rangeStart AND retrievedAt <= rangeEnd AND excluded != true
                SearchConditionRequest cond = SearchConditionRequest.group("AND",
                        Condition.of("$.retrievedAt", "GREATER_THAN", rangeStart.toString()),
                        Condition.of("$.retrievedAt", "LESS_THAN", rangeEnd.toString())
                );
                CompletableFuture<ArrayNode> fut = entityService.getItemsByCondition(Book.ENTITY_NAME, String.valueOf(Book.ENTITY_VERSION), cond, true);
                booksArray = fut.get();
            }

            List<Book> books = new ArrayList<>();
            if (booksArray != null) {
                for (JsonNode n : booksArray) {
                    try {
                        Book b = objectMapper.treeToValue(n, Book.class);
                        // skip excluded
                        // Book entity does not have excluded field in our model; rely on sourceStatus
                        if (b.getSourceStatus() != null && b.getSourceStatus().equalsIgnoreCase("ok")) {
                            books.add(b);
                        }
                    } catch (Exception ex) {
                        logger.warn("AnalyzeMetricsProcessor: failed to parse book node", ex);
                    }
                }
            }

            // compute metrics
            int totalBooks = books.size();
            int totalPages = books.stream().mapToInt(b -> b.getPageCount() == null ? 0 : b.getPageCount()).sum();
            double avgPages = totalBooks == 0 ? 0.0 : ((double) totalPages) / totalBooks;

            int topN = 5;
            if (job.getParameters() != null && job.getParameters().containsKey("topNPopular")) {
                try { topN = Integer.parseInt(String.valueOf(job.getParameters().get("topNPopular"))); } catch (Exception ignored) {}
            }

            List<Map<String, Object>> topTitles = books.stream()
                    .sorted(Comparator.comparingDouble(b -> - (b.getPopularityScore() == null ? 0.0 : b.getPopularityScore())))
                    .limit(topN)
                    .map(b -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", b.getId());
                        m.put("title", b.getTitle());
                        m.put("pageCount", b.getPageCount());
                        m.put("popularityScore", b.getPopularityScore());
                        return m;
                    }).collect(Collectors.toList());

            List<Map<String, Object>> popularTitles = books.stream()
                    .filter(b -> b.getPopularityScore() != null && b.getPopularityScore() > 0.5)
                    .limit(topN)
                    .map(b -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", b.getId());
                        m.put("title", b.getTitle());
                        m.put("descriptionSnippet", b.getDescription() == null ? null : (b.getDescription().length() > 200 ? b.getDescription().substring(0,200) : b.getDescription()));
                        m.put("excerptSnippet", b.getExcerpt() == null ? null : (b.getExcerpt().length() > 200 ? b.getExcerpt().substring(0,200) : b.getExcerpt()));
                        return m;
                    }).collect(Collectors.toList());

            Map<String, Object> publicationSummary = new LinkedHashMap<>();
            Optional<Book> newest = books.stream().filter(b -> b.getPublishDate() != null).max(Comparator.comparing(b -> b.getPublishDate()));
            Optional<Book> oldest = books.stream().filter(b -> b.getPublishDate() != null).min(Comparator.comparing(b -> b.getPublishDate()));
            publicationSummary.put("newest", newest.map(b -> Map.of("id", b.getId(), "publishDate", b.getPublishDate())).orElse(null));
            publicationSummary.put("oldest", oldest.map(b -> Map.of("id", b.getId(), "publishDate", b.getPublishDate())).orElse(null));
            Map<Integer, Long> countsByYear = books.stream().filter(b -> b.getPublishDate() != null)
                    .collect(Collectors.groupingBy(b -> b.getPublishDate().getYear(), Collectors.counting()));
            publicationSummary.put("countsByYear", countsByYear);

            WeeklyReport report = new WeeklyReport();
            report.setFetchJobId(job.getName());
            report.setWeekStartDate(rangeStart == null ? OffsetDateTime.now(ZoneOffset.UTC).minusDays(7) : rangeStart);
            report.setWeekEndDate(rangeEnd);
            report.setTotalBooks(totalBooks);
            report.setTotalPages(totalPages);
            report.setAvgPages(avgPages);
            report.setTopTitles(topTitles);
            report.setPopularTitles(popularTitles);
            report.setPublicationSummary(publicationSummary);
            report.setGenerationTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
            report.setReportStatus("generated");

            // persist report
            CompletableFuture<?> fut = entityService.addItem(WeeklyReport.ENTITY_NAME, String.valueOf(WeeklyReport.ENTITY_VERSION), report);
            try { fut.get(); } catch (InterruptedException | ExecutionException e) { logger.warn("AnalyzeMetricsProcessor: failed to persist report", e); }

            // mark job status
            job.setLastRunAt(OffsetDateTime.now(ZoneOffset.UTC));
            job.setStatus("analyzed");

        } catch (InterruptedException | ExecutionException ex) {
            logger.error("AnalyzeMetricsProcessor: entityService error", ex);
            job.setStatus("failed");
        } catch (Exception ex) {
            logger.error("AnalyzeMetricsProcessor: unexpected error", ex);
            job.setStatus("failed");
        }

        return job;
    }
}
