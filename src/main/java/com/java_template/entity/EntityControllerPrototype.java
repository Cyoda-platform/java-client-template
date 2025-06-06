```java
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
@RestController
@RequestMapping("/api/v1")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String DEFAULT_CSV_URL =
            "https://raw.githubusercontent.com/Cyoda-platform/cyoda-ai/refs/heads/ai-2.x/data/test-inputs/v1/connections/london_houses.csv";

    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for analysis jobs and their status
    private final Map<String, JobStatus> analysisJobs = new ConcurrentHashMap<>();

    // In-memory store for analysis reports (mocked)
    private final Map<String, Report> analysisReports = new ConcurrentHashMap<>();

    // In-memory subscriber store
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

    // --- DTOs ---
    @Data
    public static class RunAnalysisRequest {
        private String url;
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
        private String status;      // e.g. "queued", "running", "completed"
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
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private String subscriberId;
        private String email;
        private String status; // "subscribed"
    }

    @Data
    @AllArgsConstructor
    public static class SendEmailRequest {
        private String subject;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class SendEmailResponse {
        private String emailStatus;
        private Instant sentAt;
    }

    // --- API Endpoints ---

    /**
     * POST /api/v1/analysis/run
     * Trigger data download, analysis, and report generation.
     */
    @PostMapping("/analysis/run")
    public RunAnalysisResponse runAnalysis(@RequestBody(required = false) RunAnalysisRequest request) {
        String csvUrl = (request != null && StringUtils.hasText(request.getUrl())) ? request.getUrl() : DEFAULT_CSV_URL;
        String analysisType = (request != null && StringUtils.hasText(request.getAnalysisType())) ? request.getAnalysisType() : "summary";

        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        analysisJobs.put(jobId, new JobStatus("queued", requestedAt));
        logger.info("Received analysis request. JobId={}, URL={}, AnalysisType={}", jobId, csvUrl, analysisType);

        // Fire-and-forget processing
        CompletableFuture.runAsync(() -> processAnalysisJob(jobId, csvUrl, analysisType));

        return new RunAnalysisResponse(jobId, "queued");
    }

    /**
     * GET /api/v1/analysis/{analysisId}/report
     * Retrieve generated report for a given analysisId.
     */
    @GetMapping("/analysis/{analysisId}/report")
    public Report getReport(@PathVariable String analysisId) {
        JobStatus jobStatus = analysisJobs.get(analysisId);
        if (jobStatus == null) {
            logger.error("Report requested for unknown analysisId={}", analysisId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis job not found");
        }
        if (!"completed".equalsIgnoreCase(jobStatus.getStatus())) {
            logger.info("Report requested but analysis not completed for analysisId={}", analysisId);
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

    /**
     * POST /api/v1/subscribers
     * Add a new subscriber email.
     */
    @PostMapping("/subscribers")
    public Subscriber addSubscriber(@RequestBody AddSubscriberRequest request) {
        if (request == null || !StringUtils.hasText(request.getEmail())) {
            logger.error("Invalid subscriber email received");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be provided");
        }
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(subscriberId, request.getEmail(), "subscribed");
        subscribers.put(subscriberId, subscriber);
        logger.info("Added new subscriber: {}", subscriber.getEmail());
        return subscriber;
    }

    /**
     * GET /api/v1/subscribers
     * List all subscribers.
     */
    @GetMapping("/subscribers")
    public Collection<Subscriber> listSubscribers() {
        return subscribers.values();
    }

    /**
     * POST /api/v1/analysis/{analysisId}/send-email
     * Send report email to all subscribers.
     */
    @PostMapping("/analysis/{analysisId}/send-email")
    public SendEmailResponse sendEmail(@PathVariable String analysisId,
                                       @RequestBody(required = false) SendEmailRequest emailRequest) {
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

        // Fire-and-forget email sending (mocked)
        CompletableFuture.runAsync(() -> {
            logger.info("Sending emails for analysisId={}, subject={}", analysisId,
                    emailRequest != null ? emailRequest.getSubject() : "No Subject");
            // TODO: Implement real email sending logic here
            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException e) {
                logger.error("Email sending interrupted", e);
            }
            logger.info("Emails sent to {} subscribers", subscribers.size());
        });

        return new SendEmailResponse("sent", Instant.now());
    }

    // --- Internal Methods ---

    /**
     * Processes the analysis job: downloads CSV data, performs analysis, and stores report.
     */
    private void processAnalysisJob(String jobId, String csvUrl, String analysisType) {
        logger.info("Started processing analysis job {} with URL {}", jobId, csvUrl);
        analysisJobs.put(jobId, new JobStatus("running", Instant.now()));

        try {
            List<Map<String, String>> csvData = downloadCsv(csvUrl);
            logger.info("CSV data downloaded: {} rows", csvData.size());

            Report report = performAnalysis(jobId, csvData, analysisType);
            analysisReports.put(jobId, report);
            analysisJobs.put(jobId, new JobStatus("completed", Instant.now()));

            logger.info("Analysis job {} completed, report generated", jobId);
        } catch (Exception ex) {
            logger.error("Error processing analysis job " + jobId, ex);
            analysisJobs.put(jobId, new JobStatus("failed", Instant.now()));
        }
    }

    /**
     * Downloads CSV data from the given URL and parses it into a list of maps.
     * This is a simple CSV parser prototype (does not handle quoted commas).
     */
    private List<Map<String, String>> downloadCsv(String csvUrl) throws Exception {
        logger.info("Downloading CSV from {}", csvUrl);
        URL url = new URL(csvUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("Failed to download CSV, HTTP code: " + code);
        }

        List<Map<String, String>> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String headerLine = br.readLine();
            if (headerLine == null) {
                throw new RuntimeException("CSV file is empty");
            }
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

    /**
     * Performs a simple summary analysis on the CSV data.
     * Currently only supports "summary" analysisType.
     * Calculates mean, median for "price" field and total listings count.
     * TODO: Extend for other analysis types if needed.
     */
    private Report performAnalysis(String jobId, List<Map<String, String>> csvData, String analysisType) {
        logger.info("Performing analysis type '{}' on job {}", analysisType, jobId);

        if (!"summary".equalsIgnoreCase(analysisType)) {
            logger.warn("Unsupported analysisType '{}', defaulting to summary", analysisType);
        }

        List<Double> prices = new ArrayList<>();
        for (Map<String, String> row : csvData) {
            String priceStr = row.get("price");
            if (priceStr != null) {
                try {
                    prices.add(Double.parseDouble(priceStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid price value skipped: {}", priceStr);
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

    // --- Minimal Exception Handlers ---

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
```
