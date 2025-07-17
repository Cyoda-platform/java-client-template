package com.java_template.controller;

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

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/prototype/report")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    // Using local cache only for subscribers as minor utility entity
    private final Map<String, Set<String>> subscriberStorage = new HashMap<>();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

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

        // Business logic moved to processors, only persistence and triggering remain here

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

    // ReportJob is main entity with significant logic, replaced with entityService persistence
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

        public String getReportId() {
            return reportId;
        }

        public void setReportId(String reportId) {
            this.reportId = reportId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Instant getRequestedAt() {
            return requestedAt;
        }

        public void setRequestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(Instant completedAt) {
            this.completedAt = completedAt;
        }

        public Map<String, Object> getAnalysisResult() {
            return analysisResult;
        }

        public void setAnalysisResult(Map<String, Object> analysisResult) {
            this.analysisResult = analysisResult;
        }
    }

    public static class ReportRequest {
        @NotBlank
        private String dataUrl;
        @NotNull
        @Size(min = 1)
        private List<@Email @NotBlank String> subscribers;
        private boolean summary = true;
        @NotNull
        private List<@NotBlank String> customMetrics;

        public String getDataUrl() {
            return dataUrl;
        }

        public void setDataUrl(String dataUrl) {
            this.dataUrl = dataUrl;
        }

        public List<String> getSubscribers() {
            return subscribers;
        }

        public void setSubscribers(List<String> subscribers) {
            this.subscribers = subscribers;
        }

        public boolean isSummary() {
            return summary;
        }

        public void setSummary(boolean summary) {
            this.summary = summary;
        }

        public List<String> getCustomMetrics() {
            return customMetrics;
        }

        public void setCustomMetrics(List<String> customMetrics) {
            this.customMetrics = customMetrics;
        }
    }

    public static class ReportResponse {
        private final String status;
        private final String message;

        public ReportResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ReportStatusResponse {
        private final String reportId;
        private final String status;
        private final String generatedAt;
        private final ReportSummary summary;

        public ReportStatusResponse(String reportId, String status, String generatedAt, ReportSummary summary) {
            this.reportId = reportId;
            this.status = status;
            this.generatedAt = generatedAt;
            this.summary = summary;
        }

        public String getReportId() {
            return reportId;
        }

        public String getStatus() {
            return status;
        }

        public String getGeneratedAt() {
            return generatedAt;
        }

        public ReportSummary getSummary() {
            return summary;
        }
    }

    public static class ReportSummary {
        private final Map<String, Object> metrics;

        public ReportSummary(Map<String, Object> metrics) {
            this.metrics = metrics;
        }

        public Map<String, Object> getMetrics() {
            return metrics;
        }
    }

    public static class SubscribersRequest {
        @NotNull
        @Size(min = 1)
        private List<@Email @NotBlank String> subscribers;

        public List<String> getSubscribers() {
            return subscribers;
        }

        public void setSubscribers(List<String> subscribers) {
            this.subscribers = subscribers;
        }
    }

    public static class SubscribersResponse {
        private final List<String> subscribers;

        public SubscribersResponse(List<String> subscribers) {
            this.subscribers = subscribers;
        }

        public List<String> getSubscribers() {
            return subscribers;
        }
    }

    public static class AddSubscribersResponse {
        private final List<String> added;
        private final List<String> failed;

        public AddSubscribersResponse(List<String> added, List<String> failed) {
            this.added = added;
            this.failed = failed;
        }

        public List<String> getAdded() {
            return added;
        }

        public List<String> getFailed() {
            return failed;
        }
    }
}