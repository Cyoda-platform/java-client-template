package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, JobStatus> scrapeJobs = new ConcurrentHashMap<>();
    private final Map<String, AnalysisStatus> analysisJobs = new ConcurrentHashMap<>();
    private final Map<String, TrendResult> resultsStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String DEFAULT_NEWS_API = "https://financialmodelingprep.com/api/v3/stock_news?limit=50&apikey=demo";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/scrape-news")
    public ResponseEntity<ScrapeResponse> scrapeNews(@RequestBody @Valid ScrapeRequest request) {
        log.info("Received scrape-news request: {}", request);
        String taskId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        scrapeJobs.put(taskId, new JobStatus("started", requestedAt));
        CompletableFuture.runAsync(() -> performScrape(taskId, request));
        return ResponseEntity.ok(new ScrapeResponse(taskId, "started"));
    }

    @Async
    void performScrape(String taskId, ScrapeRequest request) {
        try {
            log.info("Starting scraping task: {}", taskId);
            String url = DEFAULT_NEWS_API;
            JsonNode newsJson = restTemplate.getForObject(url, JsonNode.class);
            if (newsJson == null) throw new Exception("Empty response from news API");
            TrendResult dummyResult = new TrendResult(taskId, "rawNews", newsJson);
            resultsStore.put(taskId, dummyResult);
            scrapeJobs.put(taskId, new JobStatus("completed", Instant.now()));
            log.info("Scraping task {} completed successfully", taskId);
        } catch (Exception e) {
            log.error("Error during scraping task {}: {}", taskId, e.getMessage(), e);
            scrapeJobs.put(taskId, new JobStatus("failed", Instant.now()));
        }
    }

    @PostMapping("/analyze-trends")
    public ResponseEntity<AnalyzeResponse> analyzeTrends(@RequestBody @Valid AnalyzeRequest request) {
        log.info("Received analyze-trends request: {}", request);
        if (!resultsStore.containsKey(request.getTaskId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "taskId not found or scraping not completed");
        }
        String analysisId = UUID.randomUUID().toString();
        analysisJobs.put(analysisId, new AnalysisStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> performTrendAnalysis(analysisId, request));
        return ResponseEntity.ok(new AnalyzeResponse(analysisId, "processing"));
    }

    @Async
    void performTrendAnalysis(String analysisId, AnalyzeRequest request) {
        try {
            log.info("Starting trend analysis {} for taskId {}", analysisId, request.getTaskId());
            TrendResult rawData = resultsStore.get(request.getTaskId());
            if (rawData == null) throw new Exception("No scraped data found for taskId " + request.getTaskId());
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
            TrendResult analyzed = new TrendResult(analysisId, request.getTrendType(), resultsNode);
            resultsStore.put(analysisId, analyzed);
            analysisJobs.put(analysisId, new AnalysisStatus("completed", Instant.now()));
            log.info("Trend analysis {} completed successfully", analysisId);
        } catch (Exception e) {
            log.error("Error during trend analysis {}: {}", analysisId, e.getMessage(), e);
            analysisJobs.put(analysisId, new AnalysisStatus("failed", Instant.now()));
        }
    }

    @GetMapping("/trends/{analysisId}")
    public ResponseEntity<TrendResult> getTrendResult(@PathVariable @NotBlank String analysisId) {
        log.info("Received getTrendResult request for analysisId: {}", analysisId);
        TrendResult result = resultsStore.get(analysisId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "analysisId not found");
        }
        return ResponseEntity.ok(result);
    }

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScrapeRequest {
        @Size(max = 5)
        private List<@NotBlank String> sites;
        @Size(max = 10)
        private List<@NotBlank String> keywords;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String dateFrom;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String dateTo;
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
        @NotBlank
        private String taskId;
        @NotBlank
        @Pattern(regexp = "sentiment|keywordFrequency|custom")
        private String trendType;
        private Map<String, Object> params; // optional, no validation
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
        private String status;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisStatus {
        private String status;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendResult {
        private String analysisId;
        private String trendType;
        private JsonNode results;
    }
}