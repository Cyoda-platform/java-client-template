package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/comments")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for reports keyed by postId
    private final Map<Integer, Report> reportStore = new ConcurrentHashMap<>();

    @PostMapping("/analyze")
    public AnalyzeResponse analyzeComments(@RequestBody AnalyzeRequest request) {
        logger.info("Received analyze request for postId={} with email={}", request.getPostId(), request.getEmail());

        // Trigger background processing (fire-and-forget)
        CompletableFuture.runAsync(() -> processComments(request.getPostId(), request.getEmail()));

        return new AnalyzeResponse("success", "Report generation started and will be sent to " + request.getEmail());
    }

    private void processComments(int postId, String email) {
        Instant startedAt = Instant.now();
        logger.info("Started processing comments for postId={} at {}", postId, startedAt);

        try {
            // Fetch comments from external API
            String url = "https://jsonplaceholder.typicode.com/comments?postId=" + postId;
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Failed to fetch comments from external API. Status code: {}", response.statusCode());
                return; // abort processing
            }

            JsonNode commentsJson = objectMapper.readTree(response.body());

            // Analyze comments (simple mock analysis)
            Report report = analyzeCommentsData(postId, commentsJson);

            // Store report
            reportStore.put(postId, report);
            logger.info("Report stored for postId={}", postId);

            // Send email with report (mock)
            sendReportEmail(email, report);

        } catch (IOException | InterruptedException e) {
            logger.error("Exception during comments processing for postId={}: {}", postId, e.getMessage());
            // TODO: implement retry or error notification
        }

        logger.info("Finished processing comments for postId={} at {}", postId, Instant.now());
    }

    private Report analyzeCommentsData(int postId, JsonNode commentsJson) {
        // TODO: Replace with real analysis logic (e.g., sentiment, keyword extraction)
        // For prototype: count word frequencies of the word "voluptate" as example

        int wordCountVoluptate = 0;
        if (commentsJson.isArray()) {
            for (JsonNode comment : commentsJson) {
                String body = comment.path("body").asText("");
                wordCountVoluptate += countOccurrences(body.toLowerCase(), "voluptate");
            }
        }

        String summary = String.format("Found %d occurrences of the word 'voluptate' in comments.", wordCountVoluptate);

        AnalysisDetails details = new AnalysisDetails(0.75, Map.of("voluptate", wordCountVoluptate));

        return new Report(postId, summary, details, Instant.now().toString());
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }

    private void sendReportEmail(String email, Report report) {
        // TODO: Replace with real email sending via SMTP or external service
        logger.info("Mock sending email report to {} with summary: {}", email, report.getSummary());
    }

    @GetMapping("/reports/{postId}")
    public Report getReport(@PathVariable("postId") int postId) {
        logger.info("Received request for report of postId={}", postId);

        Report report = reportStore.get(postId);
        if (report == null) {
            logger.error("Report not found for postId={}", postId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for postId=" + postId);
        }

        return report;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage());
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error");
    }

    // DTOs

    @Data
    public static class AnalyzeRequest {
        @NotNull
        @Positive
        private Integer postId;

        @NotNull
        @Email
        private String email;
    }

    @Data
    public static class AnalyzeResponse {
        private final String status;
        private final String message;
    }

    @Data
    public static class Report {
        private final int postId;
        private final String summary;
        private final AnalysisDetails analysisDetails;
        private final String generatedAt;
    }

    @Data
    public static class AnalysisDetails {
        private final double sentimentScore; // mock value
        private final Map<String, Integer> wordFrequency;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}