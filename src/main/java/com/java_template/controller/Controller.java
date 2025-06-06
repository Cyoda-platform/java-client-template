package com.java_template.controller;

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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/v2/cyodaentity")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private static final String DEFAULT_CSV_URL =
            "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv";

    private final ObjectMapper objectMapper;

    private final EntityService entityService;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

    // In-memory job status and report storage for demo; in production use entityService persistence
    private final Map<String, JobStatus> analysisJobs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Report> analysisReports = Collections.synchronizedMap(new HashMap<>());

    @PostMapping("/analysis/run")
    public CompletableFuture<RunAnalysisResponse> runAnalysis(@RequestBody @Valid RunAnalysisRequest request) {
        String csvUrl = StringUtils.hasText(request.getUrl()) ? request.getUrl() : DEFAULT_CSV_URL;
        String analysisType = request.getAnalysisType();

        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("url", csvUrl);
        entityNode.put("analysisType", analysisType);

        return entityService.addItem("analysisJob", ENTITY_VERSION, entityNode)
                .thenApply(uuid -> new RunAnalysisResponse(uuid.toString(), "queued"));
    }

    private void updateJobStatus(String jobId, String status, Instant requestedAtOverride) {
        Instant requestedAt = requestedAtOverride != null ? requestedAtOverride : analysisJobs.getOrDefault(jobId, new JobStatus(status, Instant.now())).getRequestedAt();
        analysisJobs.put(jobId, new JobStatus(status, requestedAt));
        logger.info("Job {} status set to {}", jobId, status);
    }

    private void persistReportEntity(Report report) {
        ObjectNode reportNode = objectMapper.createObjectNode();
        reportNode.put("analysisId", report.getAnalysisId());
        reportNode.put("generatedAt", report.getGeneratedAt().toString());

        ObjectNode statsNode = objectMapper.createObjectNode();
        statsNode.put("meanPrice", report.getSummaryStatistics().getMeanPrice());
        statsNode.put("medianPrice", report.getSummaryStatistics().getMedianPrice());
        statsNode.put("totalListings", report.getSummaryStatistics().getTotalListings());
        reportNode.set("summaryStatistics", statsNode);

        entityService.addItem("analysisReport", ENTITY_VERSION, reportNode);

        analysisReports.put(report.getAnalysisId(), report);
    }

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
        return report;
    }

    @PostMapping("/subscribers")
    public CompletableFuture<Subscriber> addSubscriber(@RequestBody @Valid AddSubscriberRequest request) {
        ObjectNode entityNode = objectMapper.createObjectNode();
        entityNode.put("email", request.getEmail());
        entityNode.put("status", "subscribed");

        return entityService.addItem("subscriber", ENTITY_VERSION, entityNode)
                .thenApply(id -> {
                    Subscriber s = new Subscriber(id.toString(), request.getEmail(), "subscribed");
                    logger.info("Subscriber added: {}", s.getEmail());
                    return s;
                });
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

        return entityService.addItem("sendEmailJob", ENTITY_VERSION, entityNode)
                .thenApply(uuid -> new SendEmailResponse("queued", Instant.now()));
    }

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
        return new Report(jobId, stats, Instant.now());
    }

    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        Collections.sort(values);
        int mid = values.size() / 2;
        if (values.size() % 2 == 0) {
            return (values.get(mid - 1) + values.get(mid)) / 2.0;
        } else {
            return values.get(mid);
        }
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