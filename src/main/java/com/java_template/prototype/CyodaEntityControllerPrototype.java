package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/api/jobs")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String EXTERNAL_BOOKS_API = "https://fakerestapi.azurewebsites.net/api/v1/Books";
    private static final String ENTITY_VERSION = ENTITY_VERSION;

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, SummaryReport> latestReport = new ConcurrentHashMap<>();

    @PostMapping(path = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> analyzeBooks(@RequestBody @Valid AnalyzeRequest request) {
        Instant triggerInstant = StringUtils.hasText(request.getTriggerDate())
                ? Instant.parse(request.getTriggerDate() + "T00:00:00Z")
                : Instant.now();

        logger.info("Received analyzeBooks request. Trigger date: {}", triggerInstant);
        String jobId = UUID.randomUUID().toString();
        latestReport.put("latest", new SummaryReport("processing", triggerInstant.toString()));

        CompletableFuture.runAsync(() -> processBookAnalysis(triggerInstant, jobId))
                .exceptionally(ex -> {
                    logger.error("Error during book analysis jobId={} : {}", jobId, ex.getMessage(), ex);
                    latestReport.put("latest", new SummaryReport("error", triggerInstant.toString()));
                    return null;
                });

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Book data analysis started and report generation will be emailed.");
        response.put("jobId", jobId);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping(path = "/reports/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SummaryReport> getLatestReport() {
        SummaryReport report = latestReport.get("latest");
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report available");
        }
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    private void processBookAnalysis(Instant triggerInstant, String jobId) {
        logger.info("Starting book analysis jobId={} at {}", jobId, triggerInstant);
        try {
            String jsonResponse = restTemplate.getForObject(URI.create(EXTERNAL_BOOKS_API), String.class);
            JsonNode booksNode = objectMapper.readTree(jsonResponse);
            if (!booksNode.isArray()) {
                throw new IllegalStateException("Expected JSON array for books");
            }

            List<Book> books = new ArrayList<>();
            for (JsonNode bookNode : booksNode) {
                Book book = new Book();
                book.setId(bookNode.path("id").asInt());
                book.setTitle(bookNode.path("title").asText(""));
                book.setDescription(bookNode.path("description").asText(""));
                book.setPageCount(bookNode.path("pageCount").asInt(0));
                book.setExcerpt(bookNode.path("excerpt").asText(""));
                book.setPublishDate(bookNode.path("publishDate").asText(""));
                books.add(book);
            }

            int totalPageCount = books.stream().mapToInt(Book::getPageCount).sum();
            Optional<String> earliestPublishDate = books.stream()
                    .map(Book::getPublishDate)
                    .filter(StringUtils::hasText)
                    .min(String::compareTo);
            Optional<String> latestPublishDate = books.stream()
                    .map(Book::getPublishDate)
                    .filter(StringUtils::hasText)
                    .max(String::compareTo);

            List<Book> popularTitles = books.stream()
                    .sorted(Comparator.comparingInt(Book::getPageCount).reversed())
                    .limit(3)
                    .collect(Collectors.toList());

            String summaryText = String.format("Analyzed %d books with total page count %d. Publication dates range from %s to %s.",
                    books.size(),
                    totalPageCount,
                    earliestPublishDate.orElse("N/A"),
                    latestPublishDate.orElse("N/A"));

            SummaryReport report = new SummaryReport();
            report.setReportDate(triggerInstant.toString());
            report.setTotalBooks(books.size());
            report.setTotalPageCount(totalPageCount);
            report.setPublicationDateRange(new PublicationDateRange(earliestPublishDate.orElse(null), latestPublishDate.orElse(null)));
            report.setPopularTitles(popularTitles);
            report.setSummaryText(summaryText);
            report.setStatus("completed");
            latestReport.put("latest", report);

            sendReportEmail(report); // TODO: implement real email integration
            logger.info("Book analysis jobId={} completed successfully", jobId);
        } catch (Exception e) {
            logger.error("Exception during book analysis jobId={}: {}", jobId, e.getMessage(), e);
            latestReport.put("latest", new SummaryReport("error", triggerInstant.toString()));
        }
    }

    private void sendReportEmail(SummaryReport report) {
        logger.info("Sending summary report email to analytics team...");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        logger.info("Summary report email sent successfully.");
    }

    @Data
    public static class AnalyzeRequest {
        @Pattern(regexp="\\d{4}-\\d{2}-\\d{2}", message="triggerDate must be in YYYY-MM-DD format")
        private String triggerDate;
    }

    @Data
    public static class Book {
        private int id;
        private String title;
        private String description;
        private int pageCount;
        private String excerpt;
        private String publishDate;
    }

    @Data
    public static class PublicationDateRange {
        private String earliest;
        private String latest;
        public PublicationDateRange(String earliest, String latest) {
            this.earliest = earliest;
            this.latest = latest;
        }
    }

    @Data
    public static class SummaryReport {
        private String status;
        private String reportDate;
        private int totalBooks;
        private int totalPageCount;
        private PublicationDateRange publicationDateRange;
        private List<Book> popularTitles;
        private String summaryText;
        public SummaryReport() {}
        public SummaryReport(String status, String reportDate) {
            this.status = status;
            this.reportDate = reportDate;
        }
    }
}