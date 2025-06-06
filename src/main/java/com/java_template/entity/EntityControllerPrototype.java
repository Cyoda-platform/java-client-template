package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String DEFAULT_CSV_URL =
            "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> analysisJobs = new ConcurrentHashMap<>();
    private final Map<String, Report> analysisReports = new ConcurrentHashMap<>();
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

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
        private String subscriberId;
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

    @PostMapping("/analysis/run")
    public RunAnalysisResponse runAnalysis(@RequestBody @Valid RunAnalysisRequest request) {
        String csvUrl = StringUtils.hasText(request.getUrl()) ? request.getUrl() : DEFAULT_CSV_URL;
        String analysisType = request.getAnalysisType();
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        analysisJobs.put(jobId, new JobStatus("queued", requestedAt));
        logger.info("Received analysis request. JobId={}, URL={}, AnalysisType={}", jobId, csvUrl, analysisType);
        CompletableFuture.runAsync(() -> processAnalysisJob(jobId, csvUrl, analysisType));
        return new RunAnalysisResponse(jobId, "queued");
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
        logger.info("Returning report for analysisId={}", analysisId);
        return report;
    }

    @PostMapping("/subscribers")
    public Subscriber addSubscriber(@RequestBody @Valid AddSubscriberRequest request) {
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(subscriberId, request.getEmail(), "subscribed");
        subscribers.put(subscriberId, subscriber);
        logger.info("Added new subscriber: {}", subscriber.getEmail());
        return subscriber;
    }

    @GetMapping("/subscribers")
    public Collection<Subscriber> listSubscribers() {
        return subscribers.values();
    }

    @PostMapping("/analysis/{analysisId}/send-email")
    public SendEmailResponse sendEmail(
            @PathVariable @NotBlank String analysisId,
            @RequestBody @Valid SendEmailRequest emailRequest) {
        JobStatus jobStatus = analysisJobs.get(analysisId);
        if (jobStatus == null || !"completed".equalsIgnoreCase(jobStatus.getStatus())) {
            logger.error("Email send requested but analysis not completed or unknown analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Analysis not completed or not found");
        }
        Report report = analysisReports.get(analysisId);
        if (report == null) {
            logger.error("No report to email for analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report not found");
        }
        CompletableFuture.runAsync(() -> {
            logger.info("Sending emails for analysisId={}, subject={}", analysisId, emailRequest.getSubject());
            // TODO: Implement real email sending logic here
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            logger.info("Emails sent to {} subscribers", subscribers.size());
        });
        return new SendEmailResponse("sent", Instant.now());
    }

    private void processAnalysisJob(String jobId, String csvUrl, String analysisType) {
        logger.info("Started processing analysis job {} with URL {}", jobId, csvUrl);
        analysisJobs.put(jobId, new JobStatus("running", Instant.now()));
        try {
            List<Map<String, String>> csvData = downloadCsv(csvUrl);
            Report report = performAnalysis(jobId, csvData, analysisType);
            analysisReports.put(jobId, report);
            analysisJobs.put(jobId, new JobStatus("completed", Instant.now()));
            logger.info("Analysis job {} completed", jobId);
        } catch (Exception ex) {
            logger.error("Error processing analysis job " + jobId, ex);
            analysisJobs.put(jobId, new JobStatus("failed", Instant.now()));
        }
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
                try { prices.add(Double.parseDouble(priceStr)); }
                catch (NumberFormatException e) { logger.warn("Invalid price skipped: {}", priceStr); }
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
        error.put("error", ex.getStatusCode().getReasonPhrase());
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