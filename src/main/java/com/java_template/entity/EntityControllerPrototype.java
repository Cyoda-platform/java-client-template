```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> scrapeJobs = new ConcurrentHashMap<>();
    private final Map<String, AnalysisStatus> analysisJobs = new ConcurrentHashMap<>();
    private final Map<String, TrendResult> resultsStore = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DEFAULT_NEWS_API = "https://financialmodelingprep.com/api/v3/stock_news?limit=50&apikey=demo"; 
    // TODO: Replace with real financial news API and key

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    // --- POST /api/scrape-news ---
    @PostMapping("/scrape-news")
    public ResponseEntity<ScrapeResponse> scrapeNews(@RequestBody(required = false) ScrapeRequest request) {
        log.info("Received scrape-news request: {}", request);

        String taskId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        scrapeJobs.put(taskId, new JobStatus("started", requestedAt));

        // Fire-and-forget scraping (mocked)
        CompletableFuture.runAsync(() -> performScrape(taskId, request));

        return ResponseEntity.ok(new ScrapeResponse(taskId, "started"));
    }

    @Async
    void performScrape(String taskId, ScrapeRequest request) {
        try {
            log.info("Starting scraping task: {}", taskId);

            // TODO: Implement actual scraping logic for multiple financial news sites.
            // For prototype, we simulate a scraping delay and save dummy data.

            // Example: fetch from a dummy API (Financial Modeling Prep free news API)
            String url = DEFAULT_NEWS_API;
            if (request != null && request.getSites() != null && !request.getSites().isEmpty()) {
                // For prototype, ignore sites param or you can extend here.
                log.info("Sites parameter provided but ignored in prototype: {}", request.getSites());
            }

            JsonNode newsJson = restTemplate.getForObject(url, JsonNode.class);
            if (newsJson == null) {
                throw new Exception("Empty response from news API");
            }

            // Save scraped data as "analysis input" under taskId (mock)
            TrendResult dummyResult = new TrendResult();
            dummyResult.setAnalysisId(taskId);
            dummyResult.setTrendType("rawNews");
            dummyResult.setResults(newsJson);

            resultsStore.put(taskId, dummyResult);

            scrapeJobs.put(taskId, new JobStatus("completed", Instant.now()));

            log.info("Scraping task {} completed successfully", taskId);
        } catch (Exception e) {
            log.error("Error during scraping task {}: {}", taskId, e.getMessage(), e);
            scrapeJobs.put(taskId, new JobStatus("failed", Instant.now()));
        }
    }

    // --- POST /api/analyze-trends ---
    @PostMapping("/analyze-trends")
    public ResponseEntity<AnalyzeResponse> analyzeTrends(@RequestBody AnalyzeRequest request) {
        log.info("Received analyze-trends request: {}", request);

        if (request == null || request.getTaskId() == null || request.getTrendType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId and trendType are required");
        }
        if (!resultsStore.containsKey(request.getTaskId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "taskId not found or scraping not completed");
        }

        String analysisId = UUID.randomUUID().toString();
        analysisJobs.put(analysisId, new AnalysisStatus("processing", Instant.now()));

        // Fire-and-forget analysis (mock)
        CompletableFuture.runAsync(() -> performTrendAnalysis(analysisId, request));

        return ResponseEntity.ok(new AnalyzeResponse(analysisId, "processing"));
    }

    @Async
    void performTrendAnalysis(String analysisId, AnalyzeRequest request) {
        try {
            log.info("Starting trend analysis {} for taskId {}", analysisId, request.getTaskId());

            // Mock: get raw news JSON stored from scrape
            TrendResult rawData = resultsStore.get(request.getTaskId());
            if (rawData == null) {
                throw new Exception("No scraped data found for taskId " + request.getTaskId());
            }

            // TODO: Implement real trend analysis logic here (e.g., sentiment analysis, keyword frequency)
            // Prototype: generate dummy time series and chart data

            TrendResult analyzed = new TrendResult();
            analyzed.setAnalysisId(analysisId);
            analyzed.setTrendType(request.getTrendType());

            // Build dummy time series and chartData JSON structure
            String dummyJson = """
            {
              "timeSeries": [
                {"date": "2024-01-01", "value": 0.7},
                {"date": "2024-01-02", "value": 0.65},
                {"date": "2024-01-03", "value": 0.75}
              ],
              "chartData": {
                "type": "line",
                "labels": ["2024-01-01", "2024-01-02", "2024-01-03"],
                "datasets": [
                  {
                    "label": "%s score",
                    "data": [0.7, 0.65, 0.75]
                  }
                ]
              }
            }
            """.formatted(request.getTrendType());

            JsonNode resultsNode = objectMapper.readTree(dummyJson);
            analyzed.setResults(resultsNode);

            resultsStore.put(analysisId, analyzed);
            analysisJobs.put(analysisId, new AnalysisStatus("completed", Instant.now()));

            log.info("Trend analysis {} completed successfully", analysisId);
        } catch (Exception e) {
            log.error("Error during trend analysis {}: {}", analysisId, e.getMessage(), e);
            analysisJobs.put(analysisId, new AnalysisStatus("failed", Instant.now()));
        }
    }

    // --- GET /api/trends/{analysisId} ---
    @GetMapping("/trends/{analysisId}")
    public ResponseEntity<TrendResult> getTrendResult(@PathVariable String analysisId) {
        log.info("Received getTrendResult request for analysisId: {}", analysisId);

        TrendResult result = resultsStore.get(analysisId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "analysisId not found");
        }

        return ResponseEntity.ok(result);
    }

    // --- Exception Handling (minimal) ---
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error: " + ex.getMessage());
    }

    // --- DTOs and Internal Classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapeRequest {
        private java.util.List<String> sites;
        private java.util.List<String> keywords;
        private DateRange dateRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private String from; // ISO date string, e.g. "2024-01-01"
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapeResponse {
        private String taskId;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeRequest {
        private String taskId;
        private String trendType; // "sentiment", "keywordFrequency", "custom"
        private Map<String, Object> params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeResponse {
        private String analysisId;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatus {
        private String status; // e.g., started, processing, completed, failed
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisStatus {
        private String status; // processing, completed, failed
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendResult {
        private String analysisId;
        private String trendType;
        private JsonNode results; // flexible JSON for time series and chart data
    }
}
```
