package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/prototype/reports")
@Validated
@RequiredArgsConstructor
@Slf4j
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Entity name constant for Report entity
    private static final String ENTITY_NAME_REPORT = "Report";

    @PostMapping("/analyze")
    public AnalyzeResponse analyzeComments(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received analyze request for postId={} with email={}", request.getPostId(), request.getEmail());
        CompletableFuture.runAsync(() -> processComments(request.getPostId(), request.getEmail()));
        return new AnalyzeResponse("success", "Report generation started and will be sent to " + request.getEmail());
    }

    private void processComments(int postId, String email) {
        Instant startedAt = Instant.now();
        logger.info("Started processing postId={} at {}", postId, startedAt);
        try {
            String url = "https://jsonplaceholder.typicode.com/comments?postId=" + postId;
            HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("Failed fetch comments. statusCode={}", response.statusCode());
                return;
            }
            JsonNode commentsJson = objectMapper.readTree(response.body());
            Report report = analyzeCommentsData(postId, commentsJson);

            // Save report entity via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME_REPORT,
                    ENTITY_VERSION,
                    report
            );
            idFuture.thenAccept(id -> logger.info("Report saved with technicalId={} for postId={}", id, postId));
            sendReportEmail(email, report);
        } catch (IOException | InterruptedException e) {
            logger.error("Error processing postId={}: {}", postId, e.getMessage());
            // TODO: retry or notify
        }
        logger.info("Finished processing postId={} at {}", postId, Instant.now());
    }

    private Report analyzeCommentsData(int postId, JsonNode commentsJson) {
        int count = 0;
        if (commentsJson.isArray()) {
            for (JsonNode comment : commentsJson) {
                String body = comment.path("body").asText("");
                count += countOccurrences(body.toLowerCase(), "voluptate");
            }
        }
        String summary = String.format("Found %d occurrences of 'voluptate'.", count);
        AnalysisDetails details = new AnalysisDetails(0.75, Map.of("voluptate", count));
        return new Report(postId, summary, details, Instant.now().toString());
    }

    private int countOccurrences(String text, String word) {
        int c = 0, idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            c++;
            idx += word.length();
        }
        return c;
    }

    private void sendReportEmail(String email, Report report) {
        // TODO: implement real email sending
        logger.info("Mock send email to {} with summary: {}", email, report.getSummary());
    }

    @GetMapping("/reports/{postId}")
    public Report getReport(@PathVariable @Positive int postId) {
        logger.info("Request report for postId={}", postId);

        // Build condition to find report by postId
        Condition cond = Condition.of("$.postId", "EQUALS", postId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<List<UUID>> allIdsFuture = entityService.getItemsByCondition(ENTITY_NAME_REPORT, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    // The result is ArrayNode, map to list of UUIDs from technicalId fields
                    return arrayNode.findValues("technicalId").stream()
                            .map(JsonNode -> UUID.fromString(JsonNode.asText()))
                            .toList();
                });

        CompletableFuture<Report> reportFuture = entityService.getItemsByCondition(ENTITY_NAME_REPORT, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> {
                    if (arrayNode.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for postId=" + postId);
                    }
                    JsonNode node = arrayNode.get(0);
                    try {
                        return objectMapper.treeToValue(node, Report.class);
                    } catch (Exception e) {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report");
                    }
                });

        return reportFuture.join(); // block here as controller method is synchronous
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
        private final double sentimentScore;
        private final Map<String, Integer> wordFrequency;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}