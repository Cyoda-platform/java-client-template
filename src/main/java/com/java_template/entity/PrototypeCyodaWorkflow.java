Certainly! Moving asynchronous and business logic from controller endpoints into workflow functions (`process{entity_name}`) is a very good design practice. It decouples the controller from processing details, enforcing single responsibility and making code easier to maintain and extend.

---

Below is an enhanced Java example based on your existing controller.  
**All async business logic is moved into corresponding workflow functions named `process{EntityName}`**.  
- The controller methods become thinner and mostly just call `entityService.addItem` with the relevant workflow function.  
- The workflow functions manipulate the entity (an `ObjectNode`) and can perform async tasks like fetching CSV, processing analysis, updating other entities, etc.  
- The workflow functions return the modified entity which will be persisted by the service.  
- We use Jackson's `ObjectNode` for entity manipulation inside workflow functions (as per your note).  
- Since workflow functions cannot modify the current entity by calling add/update/delete on the same entity model (to avoid recursion), they may add/update other entityModels if needed.

---

### Complete updated Java code with moved async logic into workflow functions

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/v2/cyodaentity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String DEFAULT_CSV_URL =
            "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    public static class RunAnalysisRequest {
        @Pattern(regexp = "^https?://.*", message = "Invalid URL format")
        private String url;

        @NotBlank(message = "analysisType must not be blank")
        private String analysisType;
    }

    @Data
    @AllArgsConstructor
    public static class RunAnalysisResponse {
        private String analysisId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SummaryStatistics {
        private Double meanPrice;
        private Double medianPrice;
        private Integer totalListings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Report {
        private String analysisId;
        private SummaryStatistics summaryStatistics;
        private Instant generatedAt;
    }

    @Data
    public static class AddSubscriberRequest {
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private String technicalId; // store technicalId instead of subscriberId
        private String email;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class SendEmailRequest {
        @NotBlank(message = "Subject must not be blank")
        @Size(max = 255, message = "Subject must be at most 255 characters")
        private String subject;

        @NotBlank(message = "Message must not be blank")
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class SendEmailResponse {
        private String emailStatus;
        private Instant sentAt;
    }

    // Note: analysisJobs and analysisReports moved to entityService persistence instead of local maps
    // But since you said we can add supplementary/raw data entities of different entityModel inside workflows,
    // we will keep them here for demo but in real code you can persist them using entityService as well.

    private final Map<String, JobStatus> analysisJobs = new HashMap<>();
    private final Map<String, Report> analysisReports = new HashMap<>();

    // Controller endpoint becomes very thin - just create entity JSON and call addItem with workflow
    @PostMapping("/analysis/run")
    public CompletableFuture<RunAnalysisResponse> runAnalysis(@RequestBody @Valid RunAnalysisRequest request) {
        String csvUrl = StringUtils.hasText(request.getUrl()) ? request.getUrl() : DEFAULT_CSV_URL;
        String analysisType = request.getAnalysisType();

        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("url", csvUrl);
        entityNode.put("analysisType", analysisType);

        // Call addItem with workflow function processAnalysisJob
        // This workflow function will handle job creation, fetching CSV, analysis, etc asynchronously
        return entityService.addItem("analysisJob", ENTITY_VERSION, entityNode, this::processAnalysisJob)
                .thenApply(uuid -> new RunAnalysisResponse(uuid.toString(), "queued"));
    }

    // Workflow function for analysisJob entity that runs async analysis pipeline
    private CompletableFuture<ObjectNode> processAnalysisJob(ObjectNode entity) {
        // Step 1. Assign unique job id as UUID string
        String jobId = UUID.randomUUID().toString();
        entity.put("jobId", jobId);
        entity.put("status", "queued");
        entity.put("requestedAt", Instant.now().toString());

        // Step 2. Start async processing chain (fire and forget inside workflow is allowed)
        CompletableFuture.runAsync(() -> {
            try {
                updateJobStatus(jobId, "running");
                List<Map<String, String>> csvData = downloadCsv(entity.get("url").asText());
                Report report = performAnalysis(jobId, csvData, entity.get("analysisType").asText());

                // Persist report entity as a different entityModel "analysisReport"
                ObjectNode reportNode = objectMapper.createObjectNode();
                reportNode.put("analysisId", report.getAnalysisId());
                reportNode.put("generatedAt", report.getGeneratedAt().toString());

                ObjectNode statsNode = objectMapper.createObjectNode();
                statsNode.put("meanPrice", report.getSummaryStatistics().getMeanPrice());
                statsNode.put("medianPrice", report.getSummaryStatistics().getMedianPrice());
                statsNode.put("totalListings", report.getSummaryStatistics().getTotalListings());
                reportNode.set("summaryStatistics", statsNode);

                entityService.addItem("analysisReport", ENTITY_VERSION, reportNode, this::processAnalysisReport);

                updateJobStatus(jobId, "completed");
            } catch (Exception ex) {
                logger.error("Error in analysis workflow", ex);
                updateJobStatus(jobId, "failed");
            }
        });

        // Return entity with jobId and queued status so it will be persisted
        return CompletableFuture.completedFuture(entity);
    }

    // Dummy workflow for analysisReport (can be used for additional processing if needed)
    private CompletableFuture<ObjectNode> processAnalysisReport(ObjectNode entity) {
        // For now, no modifications, just return entity
        return CompletableFuture.completedFuture(entity);
    }

    // Utility to update job status in local map (in real app persist this with entityService)
    private void updateJobStatus(String jobId, String status) {
        synchronized (analysisJobs) {
            JobStatus current = analysisJobs.getOrDefault(jobId, new JobStatus(status, Instant.now()));
            analysisJobs.put(jobId, new JobStatus(status, current.getRequestedAt()));
        }
        logger.info("Job {} status updated to {}", jobId, status);
    }

    // Controller endpoint for getting report is thin - just get entity from local map or entityService
    @GetMapping("/analysis/{analysisId}/report")
    public Report getReport(@PathVariable @NotBlank String analysisId) {
        JobStatus jobStatus = analysisJobs.get(analysisId);
        if (jobStatus == null) {
            logger.error("Report requested for unknown analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis job not found");
        }
        if (!"completed".equalsIgnoreCase(jobStatus.getStatus())) {
            logger.info("Analysis not completed for analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.ACCEPTED, "Analysis is not completed yet");
        }
        Report report = analysisReports.get(analysisId);
        if (report == null) {
            logger.error("No report found for completed analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report generation error");
        }
        logger.info("Returning report for analysisId={}", analysisId);
        return report;
    }

    // Controller endpoint to add subscriber with workflow function
    @PostMapping("/subscribers")
    public CompletableFuture<Subscriber> addSubscriber(@RequestBody @Valid AddSubscriberRequest request) {
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("email", request.getEmail());
        entityNode.put("status", "subscribed");

        // Workflow function processSubscriber handles any async logic before persisting subscriber
        return entityService.addItem("subscriber", ENTITY_VERSION, entityNode, this::processSubscriber)
                .thenApply(id -> {
                    Subscriber s = new Subscriber(id.toString(), request.getEmail(), "subscribed");
                    logger.info("Subscriber added: {}", s.getEmail());
                    return s;
                });
    }

    // Workflow function for subscriber entity
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // Possible async tasks like sending welcome email, validation etc.
        // We'll simulate a fire-and-forget async task here (e.g. sending welcome email)
        CompletableFuture.runAsync(() -> {
            String email = entity.get("email").asText();
            logger.info("Sending welcome email to {}", email);
            // TODO: real email sending logic here
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            logger.info("Welcome email sent to {}", email);
        });

        // Return entity as is, possibly after modifying any fields
        return CompletableFuture.completedFuture(entity);
    }

    @GetMapping("/subscribers")
    public CompletableFuture<List<Subscriber>> listSubscribers() {
        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Subscriber> subs = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Subscriber s = objectMapper.convertValue(node, Subscriber.class);
                        subs.add(s);
                    }
                    return subs;
                });
    }

    @PostMapping("/analysis/{analysisId}/send-email")
    public CompletableFuture<SendEmailResponse> sendEmail(
            @PathVariable @NotBlank String analysisId,
            @RequestBody @Valid SendEmailRequest emailRequest) {

        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("analysisId", analysisId);
        entityNode.put("subject", emailRequest.getSubject());
        entityNode.put("message", emailRequest.getMessage());

        // Workflow function processSendEmail will handle async email sending
        return entityService.addItem("sendEmailJob", ENTITY_VERSION, entityNode, this::processSendEmail)
                .thenApply(uuid -> new SendEmailResponse("queued", Instant.now()));
    }

    // Workflow function for sendEmailJob entity asynchronously sends emails
    private CompletableFuture<ObjectNode> processSendEmail(ObjectNode entity) {
        // Fire and forget async email sending
        CompletableFuture.runAsync(() -> {
            String analysisId = entity.get("analysisId").asText();
            String subject = entity.get("subject").asText();
            String message = entity.get("message").asText();

            logger.info("Sending email for analysisId={}, subject={}", analysisId, subject);

            // Check if analysis is completed before sending emails
            JobStatus jobStatus = analysisJobs.get(analysisId);
            if (jobStatus == null || !"completed".equalsIgnoreCase(jobStatus.getStatus())) {
                logger.warn("Cannot send email, analysis not completed or unknown analysisId={}", analysisId);
                return;
            }

            // Simulate email sending (replace with real implementation)
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            logger.info("Emails sent for analysisId={}", analysisId);
        });

        return CompletableFuture.completedFuture(entity);
    }

    // Utility methods for CSV download and analysis

    private List<Map<String, String>> downloadCsv(String csvUrl) throws Exception {
        URL url = new URL(csvUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Failed to download CSV, HTTP code: " + conn.getResponseCode());
        }
        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new RuntimeException("CSV file is empty");
            String[] headers = headerLine.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, String> record = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    record.put(headers[i].trim(), values[i].trim());
                }
                records.add(record);
            }
        }
        return records;
    }

    private Report performAnalysis(String jobId, List<Map<String, String>> csvData, String analysisType) {
        List<Double> prices = new ArrayList<>();
        for (Map<String, String> row : csvData) {
            String priceStr = row.get("price");
            if (priceStr != null) {
                try {
                    prices.add(Double.parseDouble(priceStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid price skipped: {}", priceStr);
                }
            }
        }
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double median = calculateMedian(prices);
        int total = csvData.size();
        SummaryStatistics stats = new SummaryStatistics(mean, median, total);
        Report report = new Report(jobId, stats, Instant.now());

        // Also store in local map for demo - in real app persist report entity
        analysisReports.put(jobId, report);

        return report;
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        Collections.sort(values);
        int mid = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(mid - 1) + values.get(mid)) / 2.0
                : values.get(mid);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal Server Error");
        error.put("message", ex.getMessage());
        return error;
    }

}
```

---

### Summary of refactoring:
- `runAnalysis` now immediately creates a simple entity with URL and analysisType, then calls `entityService.addItem` with `processAnalysisJob` workflow.
- `processAnalysisJob` asynchronously:
  - assigns a jobId, marks status queued
  - downloads CSV, performs analysis
  - creates new `analysisReport` entity (a different entityModel) and persists it with its own workflow
  - updates job status in-memory map (in real app should persist)
- `addSubscriber` endpoint creates subscriber entity and passes to `processSubscriber` workflow, which performs async welcome email sending.
- `sendEmail` endpoint creates a `sendEmailJob` entity and passes to `processSendEmail` workflow, which asynchronously sends emails.
- All major async processing is moved out of controllers into workflow functions.
- The controllers only:
  - validate input,
  - build initial entity JSON,
  - call `entityService.addItem` with appropriate workflow function,
  - return immediate response with job/entity ID or status.

This makes the code more robust, maintainable, and aligned with the new `entityService.addItem` contract requiring workflow functions.

---

If you want, I can help further with:
- Persisting job statuses and reports as entities instead of local maps,
- More detailed validation inside workflow functions,
- Or any other improvements. Just ask!