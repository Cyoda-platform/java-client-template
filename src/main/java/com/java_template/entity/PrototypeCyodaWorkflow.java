Certainly! I'll update your `CyodaEntityControllerPrototype` to:

1. Add a workflow function `processCyodaEntity` with the required signature.
2. Update usage of `entityService.addItem` to include this workflow function.
3. Since your current code snippet does not show any existing call to `entityService.addItem`, for demonstration I'll add a new example method `addCyodaEntity` which uses the updated `addItem` method with the workflow.

Here is the complete updated Java code with these changes:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<String, JobStatus> entityJobs = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, ReportResult> reports = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<String> staticSubscribers = List.of(
            "user1@example.com",
            "user2@example.com"
            // TODO: Replace with actual subscriber source if needed
    );

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AnalyzeRequest {
        @NotBlank
        private String csvUrl;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResponse {
        private String taskId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResult {
        private String taskId;
        private String status; // completed | pending | failed
        private SummaryStatistics summaryStatistics;
        private BasicTrends basicTrends;
        private boolean emailSent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SummaryStatistics {
        private Double meanPrice;
        private Double medianPrice;
        private Integer totalListings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BasicTrends {
        private String priceTrend;
    }

    @Data
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    /**
     * Workflow function to process CyodaEntity before persistence.
     * This function takes the entity data as input, modifies or processes it,
     * and returns the modified entity asynchronously.
     *
     * Note: This function must NOT call add/update/delete on the same entity model to avoid infinite recursion.
     */
    private CompletableFuture<Map<String, Object>> processCyodaEntity(Map<String, Object> entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Processing CyodaEntity workflow for entity: {}", entity);
            // Example processing: add/update some fields before persistence
            entity.put("processedAt", Instant.now().toString());
            entity.put("status", "processed");
            // Potentially add/get other entities of different models here if needed
            return entity;
        }, executor);
    }

    /**
     * Example endpoint to add a CyodaEntity using the new entityService.addItem API
     * with workflow function.
     * 
     * Adjust entityModel and ENTITY_VERSION according to your configuration.
     */
    @PostMapping("/add")
    public CompletableFuture<ResponseEntity<Map<String, UUID>>> addCyodaEntity(@RequestBody Map<String, Object> data) {
        logger.info("Received request to add CyodaEntity: {}", data);

        // Replace "{entity_name}" with actual entity model name, e.g. "cyodaEntity"
        String entityModel = "cyodaEntity";

        // ENTITY_VERSION constant from your config
        int entityVersion = ENTITY_VERSION;

        // Call addItem with workflow function processCyodaEntity
        CompletableFuture<UUID> idFuture = entityService.addItem(
                entityModel,
                entityVersion,
                data,
                this::processCyodaEntity
        );

        return idFuture.thenApply(id -> {
            logger.info("Successfully added CyodaEntity with id: {}", id);
            return ResponseEntity.ok(Map.of("id", id));
        }).exceptionally(ex -> {
            logger.error("Failed to add CyodaEntity: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add entity");
        });
    }

    @PostMapping("/data/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeData(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received analyze request for CSV URL: {}", request.getCsvUrl());
        if (request.getCsvUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csvUrl must be provided");
        }

        String taskId = UUID.randomUUID().toString();
        entityJobs.put(taskId, new JobStatus("processing", Instant.now()));

        CompletableFuture.runAsync(() -> processAnalysis(taskId, request.getCsvUrl()), executor);

        return ResponseEntity.ok(new AnalyzeResponse(taskId, "started"));
    }

    @GetMapping("/report/{taskId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable String taskId) {
        logger.info("Fetching report for taskId: {}", taskId);
        ReportResult report = reports.get(taskId);
        if (report == null) {
            JobStatus jobStatus = entityJobs.get(taskId);
            if (jobStatus == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task ID not found");
            }
            report = new ReportResult(taskId, jobStatus.getStatus(), null, null, false);
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        logger.info("Returning static subscribers list");
        return ResponseEntity.ok(new SubscribersResponse(staticSubscribers));
    }

    private void processAnalysis(String taskId, String csvUrl) {
        try {
            logger.info("Starting analysis for taskId: {} with CSV URL: {}", taskId, csvUrl);
            String csvData = downloadCsv(csvUrl);
            SummaryStatistics stats = mockAnalyzeCsv(csvData);
            BasicTrends trends = mockDetermineTrends(stats);
            ReportResult report = new ReportResult(taskId, "completed", stats, trends, false);
            reports.put(taskId, report);
            sendEmailReportAsync(taskId, report);
            entityJobs.put(taskId, new JobStatus("completed", Instant.now()));
            logger.info("Analysis and email completed for taskId: {}", taskId);
        } catch (Exception e) {
            logger.error("Error processing analysis for taskId {}: {}", taskId, e.getMessage(), e);
            entityJobs.put(taskId, new JobStatus("failed", Instant.now()));
            reports.put(taskId, new ReportResult(taskId, "failed", null, null, false));
        }
    }

    private String downloadCsv(String csvUrl) throws Exception {
        logger.info("Downloading CSV from URL: {}", csvUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(csvUrl))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to download CSV, status code: " + response.statusCode());
        }
        return response.body();
    }

    private SummaryStatistics mockAnalyzeCsv(String csvData) {
        logger.info("Mock analyzing CSV data (length={} chars)", csvData.length());
        return new SummaryStatistics(500000.0, 450000.0, 1000);
    }

    private BasicTrends mockDetermineTrends(SummaryStatistics stats) {
        logger.info("Mock determining trends based on summary statistics");
        String trend = "stable";
        if (stats.getMeanPrice() != null && stats.getMeanPrice() > 600000) {
            trend = "increasing";
        }
        return new BasicTrends(trend);
    }

    @Async
    private void sendEmailReportAsync(String taskId, ReportResult report) {
        logger.info("Mock sending email report for taskId: {} to subscribers: {}", taskId, staticSubscribers);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        report.setEmailSent(true);
        reports.put(taskId, report);
        logger.info("Mock email sent for taskId: {}", taskId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
```

---

### Explanation of changes made:

- Added method `processCyodaEntity` which follows the naming convention `process{entity_name}` (in this case, `CyodaEntity` → `processCyodaEntity`).
- This method is asynchronous and returns a `CompletableFuture<Map<String,Object>>`, taking the entity map as input and returning the modified entity map.
- Added a new example POST endpoint `/add` to demonstrate usage of the new `entityService.addItem` method with the workflow parameter.
- `entityService.addItem` is called with 4 arguments: `entityModel`, `entityVersion`, `entity` (data), and `workflow` function reference (`this::processCyodaEntity`).
- The workflow function modifies the entity by adding some fields before persistence.
- Error handling in the new endpoint is integrated with Spring's `ResponseStatusException` for clean error responses.
- No infinite recursion can occur because the workflow method does not trigger add/update/delete on the same entity model.

If you already have a place where you call `entityService.addItem`, you can update that call by adding the workflow function accordingly and implementing a similar workflow function as shown.

Let me know if you want me to integrate this workflow function into an existing method or handle a specific entity!