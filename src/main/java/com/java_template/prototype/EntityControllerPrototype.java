```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, ReportStatus> reportJobs = new ConcurrentHashMap<>();
    private final Map<String, SubscriberList> subscriberStorage = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * POST /prototype/api/report/generate
     * Trigger data download, analysis, and email sending.
     */
    @PostMapping(path = "/report/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportResponse generateReport(@RequestBody @Validated ReportRequest request) {
        logger.info("Received report generation request for URL: {}", request.getDataUrl());

        // Validate URL (basic)
        try {
            URI uri = URI.create(request.getDataUrl());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL scheme");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid URL provided: {}", request.getDataUrl(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataUrl format");
        }

        // Validate subscribers list not empty
        if (request.getSubscribers() == null || request.getSubscribers().isEmpty()) {
            logger.error("Subscribers list is empty");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscribers list cannot be empty");
        }

        // Generate a reportId for tracking
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Initialize job status
        reportJobs.put(reportId, new ReportStatus("processing", requestedAt, null, null));

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting async report generation for reportId {}", reportId);

                // 1. Download CSV data from URL
                List<Map<String, String>> csvData = downloadCsvData(request.getDataUrl());

                // 2. Analyze data (simulate pandas analysis)
                Map<String, Object> analysisResult = analyzeData(csvData, request.getAnalysisOptions());

                // 3. Send email report (mocked)
                sendEmailReport(request.getSubscribers(), analysisResult);

                // 4. Update report status as completed
                reportJobs.put(reportId, new ReportStatus("completed", requestedAt, Instant.now(), analysisResult));

                logger.info("Report {} generation completed successfully", reportId);

            } catch (Exception e) {
                logger.error("Error during report generation for reportId {}: {}", reportId, e.getMessage(), e);
                reportJobs.put(reportId, new ReportStatus("failed", requestedAt, Instant.now(), null));
            }
        });
        // TODO: Replace CompletableFuture with @Async managed bean/service for better thread management

        return new ReportResponse("started", "Report generation started with id: " + reportId);
    }

    /**
     * GET /prototype/api/report/status/{reportId}
     * Retrieve status and results of a report.
     */
    @GetMapping(path = "/report/status/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportStatusResponse getReportStatus(@PathVariable("reportId") String reportId) {
        logger.info("Fetching report status for reportId {}", reportId);
        ReportStatus status = reportJobs.get(reportId);

        if (status == null) {
            logger.error("Report ID not found: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ReportId not found");
        }

        return new ReportStatusResponse(
                reportId,
                status.getStatus(),
                status.getRequestedAt().toString(),
                status.getAnalysisResult() != null ? new ReportSummary(status.getAnalysisResult()) : null
        );
    }

    /**
     * GET /prototype/api/subscribers
     * Return current subscriber list.
     */
    @GetMapping(path = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscribersResponse getSubscribers() {
        logger.info("Fetching subscriber list");
        // For prototyping, we store a single subscriber list keyed by "default"
        SubscriberList list = subscriberStorage.get("default");
        if (list == null) {
            list = new SubscriberList(new HashSet<>());
            subscriberStorage.put("default", list);
        }
        return new SubscribersResponse(new ArrayList<>(list.getSubscribers()));
    }

    /**
     * POST /prototype/api/subscribers
     * Add emails to subscriber list.
     */
    @PostMapping(path = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AddSubscribersResponse addSubscribers(@RequestBody @Validated SubscribersRequest request) {
        logger.info("Adding subscribers: {}", request.getSubscribers());

        SubscriberList list = subscriberStorage.get("default");
        if (list == null) {
            list = new SubscriberList(new HashSet<>());
            subscriberStorage.put("default", list);
        }

        List<String> added = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String email : request.getSubscribers()) {
            if (isValidEmail(email)) {
                if (list.getSubscribers().add(email)) {
                    added.add(email);
                }
            } else {
                failed.add(email);
            }
        }

        logger.info("Subscribers added: {}, failed: {}", added, failed);

        return new AddSubscribersResponse(added, failed);
    }

    // --- Helper methods ---

    private boolean isValidEmail(String email) {
        // Simple basic validation, can be improved
        return email != null && email.contains("@");
    }

    private List<Map<String, String>> downloadCsvData(String url) throws Exception {
        logger.info("Downloading CSV data from {}", url);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download CSV data, status code: " + response.statusCode());
        }

        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("Empty CSV data");
            }
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> record = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    record.put(headers[i].trim(), values[i].trim());
                }
                records.add(record);
            }
        }

        logger.info("Downloaded {} CSV records", records.size());
        return records;
    }

    private Map<String, Object> analyzeData(List<Map<String, String>> data, AnalysisOptions options) {
        logger.info("Analyzing data with options: {}", options);

        Map<String, Object> results = new HashMap<>();
        if (data.isEmpty()) {
            return results;
        }

        // Example: Calculate average of numeric column "price" if present
        if (options != null && options.isSummary()) {
            double sum = 0;
            int count = 0;
            for (Map<String, String> row : data) {
                String priceStr = row.get("price");
                if (priceStr != null && !priceStr.isBlank()) {
                    try {
                        double price = Double.parseDouble(priceStr);
                        sum += price;
                        count++;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (count > 0) {
                results.put("averagePrice", sum / count);
            }
            results.put("count", count);
        }

        // TODO: Implement customMetrics if required (currently ignored)
        return results;
    }

    private void sendEmailReport(List<String> subscribers, Map<String, Object> analysisResult) {
        // TODO: Replace this mock with real email sending logic integrated with email service
        logger.info("Sending email report to subscribers: {}", subscribers);
        logger.info("Report content summary: {}", analysisResult);
        // Fire-and-forget simulation
    }

    // --- DTO Classes ---

    @Data
    public static class ReportRequest {
        @NotBlank
        private String dataUrl;

        private List<@Email String> subscribers;

        private AnalysisOptions analysisOptions;
    }

    @Data
    public static class AnalysisOptions {
        private boolean summary = true;
        private List<String> customMetrics;
    }

    @Data
    public static class ReportResponse {
        private final String status;
        private final String message;
    }

    @Data
    public static class ReportStatus {
        private final String status;
        private final Instant requestedAt;
        private final Instant completedAt;
        private final Map<String, Object> analysisResult;
    }

    @Data
    public static class ReportStatusResponse {
        private final String reportId;
        private final String status;
        private final String generatedAt;
        private final ReportSummary summary;
    }

    @Data
    public static class ReportSummary {
        private final Map<String, Object> metrics;

        public ReportSummary(Map<String, Object> metrics) {
            this.metrics = metrics;
        }
    }

    @Data
    public static class SubscribersRequest {
        private List<@Email String> subscribers;
    }

    @Data
    public static class SubscribersResponse {
        private final List<String> subscribers;
    }

    @Data
    public static class AddSubscribersResponse {
        private final List<String> added;
        private final List<String> failed;
    }

    @Data
    public static class SubscriberList {
        private final Set<String> subscribers;
    }

    // --- Exception Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
    }
}
```