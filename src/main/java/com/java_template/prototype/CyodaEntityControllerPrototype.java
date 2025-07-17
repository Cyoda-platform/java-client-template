package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/prototype/report")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Using local cache only for subscribers as minor utility entity
    private final Map<String, Set<String>> subscriberStorage = new HashMap<>();

    @PostMapping("/generate")
    public ReportResponse generateReport(@RequestBody @Valid ReportRequest request) {
        logger.info("Received report generation request for URL: {}", request.getDataUrl());
        try {
            URI uri = URI.create(request.getDataUrl());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL scheme");
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid URL provided: {}", request.getDataUrl(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid dataUrl format");
        }
        String reportId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Create ReportJob entity with status "processing"
        ReportJob reportJob = new ReportJob(reportId, "processing", requestedAt, null, null);

        try {
            entityService.addItem("ReportJob", ENTITY_VERSION, reportJob).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to create ReportJob entity: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create report job");
        }

        CompletableFuture.runAsync(() -> {
            try {
                logger.info("Starting async report generation for reportId {}", reportId);
                List<Map<String, String>> csvData = downloadCsvData(request.getDataUrl());
                Map<String, Object> analysisResult = analyzeData(csvData, request.isSummary(), request.getCustomMetrics());
                sendEmailReport(request.getSubscribers(), analysisResult);

                // Update ReportJob entity with completed status and analysis result
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.reportId", "EQUALS", reportId)
                );

                try {
                    CompletableFuture<List<ObjectNode>> itemsFuture = entityService.getItemsByCondition("ReportJob", ENTITY_VERSION, condition);
                    List<ObjectNode> items = itemsFuture.get();
                    if (items.isEmpty()) {
                        logger.error("ReportJob not found for reportId {}", reportId);
                        return;
                    }
                    ObjectNode reportJobNode = items.get(0);
                    UUID technicalId = UUID.fromString(reportJobNode.get("technicalId").asText());

                    ReportJob updatedJob = new ReportJob(reportId, "completed", requestedAt, Instant.now(), analysisResult);

                    entityService.updateItem("ReportJob", ENTITY_VERSION, technicalId, updatedJob).get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to update ReportJob entity: {}", e.getMessage(), e);
                }

                logger.info("Report {} generation completed successfully", reportId);
            } catch (Exception e) {
                logger.error("Error during report generation for reportId {}: {}", reportId, e.getMessage(), e);
                // Update status to failed
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.reportId", "EQUALS", reportId)
                );
                try {
                    CompletableFuture<List<ObjectNode>> itemsFuture = entityService.getItemsByCondition("ReportJob", ENTITY_VERSION, condition);
                    List<ObjectNode> items = itemsFuture.get();
                    if (items.isEmpty()) {
                        logger.error("ReportJob not found for reportId {} while setting failed", reportId);
                        return;
                    }
                    ObjectNode reportJobNode = items.get(0);
                    UUID technicalId = UUID.fromString(reportJobNode.get("technicalId").asText());

                    ReportJob failedJob = new ReportJob(reportId, "failed", Instant.now(), Instant.now(), null);

                    entityService.updateItem("ReportJob", ENTITY_VERSION, technicalId, failedJob).get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Failed to update ReportJob to failed status: {}", ex.getMessage(), ex);
                }
            }
        });

        return new ReportResponse("started", "Report generation started with id: " + reportId);
    }

    @GetMapping("/status/{reportId}")
    public ReportStatusResponse getReportStatus(@PathVariable @NotBlank String reportId) throws Exception {
        logger.info("Fetching report status for reportId {}", reportId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.reportId", "EQUALS", reportId)
        );
        CompletableFuture<List<ObjectNode>> itemsFuture = entityService.getItemsByCondition("ReportJob", ENTITY_VERSION, condition);
        List<ObjectNode> items = itemsFuture.get();
        if (items.isEmpty()) {
            logger.error("Report ID not found: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ReportId not found");
        }
        ObjectNode reportJobNode = items.get(0);
        String status = reportJobNode.get("status").asText();
        String requestedAt = reportJobNode.has("requestedAt") && !reportJobNode.get("requestedAt").isNull() ? reportJobNode.get("requestedAt").asText() : null;
        String completedAt = reportJobNode.has("completedAt") && !reportJobNode.get("completedAt").isNull() ? reportJobNode.get("completedAt").asText() : null;
        Map<String, Object> analysisResult = null;
        if (reportJobNode.has("analysisResult") && !reportJobNode.get("analysisResult").isNull()) {
            analysisResult = objectMapper.convertValue(reportJobNode.get("analysisResult"), Map.class);
        }

        return new ReportStatusResponse(
                reportId,
                status,
                completedAt != null ? completedAt : requestedAt,
                analysisResult != null ? new ReportSummary(analysisResult) : null
        );
    }

    @GetMapping("/subscribers")
    public SubscribersResponse getSubscribers() {
        logger.info("Fetching subscriber list");
        Set<String> list = subscriberStorage.getOrDefault("default", new HashSet<>());
        return new SubscribersResponse(new ArrayList<>(list));
    }

    @PostMapping("/subscribers")
    public AddSubscribersResponse addSubscribers(@RequestBody @Valid SubscribersRequest request) {
        logger.info("Adding subscribers: {}", request.getSubscribers());
        Set<String> list = subscriberStorage.computeIfAbsent("default", k -> new HashSet<>());
        List<String> added = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String email : request.getSubscribers()) {
            if (list.add(email)) added.add(email);
            else failed.add(email);
        }
        logger.info("Subscribers added: {}, failed: {}", added, failed);
        return new AddSubscribersResponse(added, failed);
    }

    private List<Map<String, String>> downloadCsvData(String url) throws Exception {
        logger.info("Downloading CSV from {}", url);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<java.io.InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Failed to download CSV, status: " + resp.statusCode());
        }
        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new RuntimeException("Empty CSV");
            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                Map<String, String> record = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    record.put(headers[i].trim(), values.length > i ? values[i].trim() : "");
                }
                records.add(record);
            }
        }
        logger.info("Downloaded {} records", records.size());
        return records;
    }

    private Map<String, Object> analyzeData(List<Map<String, String>> data, boolean summary, List<String> customMetrics) {
        logger.info("Analyzing data, summary={}, metrics={}", summary, customMetrics);
        Map<String, Object> results = new HashMap<>();
        if (data.isEmpty()) return results;
        if (summary) {
            double sum = 0;
            int count = 0;
            for (Map<String, String> row : data) {
                String ps = row.get("price");
                if (ps != null && !ps.isBlank()) {
                    try {
                        sum += Double.parseDouble(ps);
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            }
            if (count > 0) results.put("averagePrice", sum / count);
            results.put("count", count);
        }
        // TODO: implement customMetrics
        return results;
    }

    private void sendEmailReport(List<String> subs, Map<String, Object> analysisResult) {
        logger.info("Mock send email to {} with {}", subs, analysisResult);
        // TODO: integrate real email service
    }

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
    public Map<String, Object> handleException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        return Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "Internal server error"
        );
    }

    // ReportJob is main entity with significant logic, replaced with entityService
    @Data
    public static class ReportJob {
        private String reportId;
        private String status;
        private Instant requestedAt;
        private Instant completedAt;
        private Map<String, Object> analysisResult;

        public ReportJob() {}

        public ReportJob(String reportId, String status, Instant requestedAt, Instant completedAt, Map<String, Object> analysisResult) {
            this.reportId = reportId;
            this.status = status;
            this.requestedAt = requestedAt;
            this.completedAt = completedAt;
            this.analysisResult = analysisResult;
        }
    }

    @Data
    public static class ReportRequest {
        @NotBlank
        private String dataUrl;
        @NotNull
        @Size(min = 1)
        private List<@Email @NotBlank String> subscribers;
        private boolean summary = true;
        @NotNull
        private List<@NotBlank String> customMetrics;
    }

    @Data
    public static class ReportResponse {
        private final String status;
        private final String message;
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
    }

    @Data
    public static class SubscribersRequest {
        @NotNull
        @Size(min = 1)
        private List<@Email @NotBlank String> subscribers;
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
}