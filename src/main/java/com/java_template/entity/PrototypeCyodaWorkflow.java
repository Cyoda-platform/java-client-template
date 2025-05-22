```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-comments")
@Validated
@Slf4j
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ConcurrentMap<Integer, AnalysisReport> analysisReports = new ConcurrentHashMap<>();

    private final EntityService entityService;
    private static final String ENTITY_NAME = "comment";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeComments(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received analyze request for postId={} and email={}", request.getPostId(), request.getEmail());
        CompletableFuture.runAsync(() -> processAnalysis(request.getPostId(), request.getEmail()));
        AnalyzeResponse response = new AnalyzeResponse(
            "processing",
            "Analysis started for postId " + request.getPostId() + ", report will be sent to " + request.getEmail()
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/report/{postId}")
    public ResponseEntity<AnalysisReport> getReport(@PathVariable @Min(1) Integer postId) {
        logger.info("Fetching report for postId={}", postId);
        AnalysisReport report = analysisReports.get(postId);
        if (report == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "No report found for postId " + postId);
        }
        return ResponseEntity.ok(report);
    }

    @Async
    void processAnalysis(int postId, String email) {
        logger.info("Starting analysis for postId={}", postId);
        try {
            URI uri = new URI("https://jsonplaceholder.typicode.com/comments?postId=" + postId);
            String jsonResponse = restTemplate.getForObject(uri, String.class);
            if (jsonResponse == null) {
                updateReportWithError(postId, email, "Failed to fetch comments: empty response");
                return;
            }
            JsonNode commentsNode = objectMapper.readTree(jsonResponse);
            int total = commentsNode.size();
            int pos = 0, neg = 0, neu = 0;
            for (JsonNode comment : commentsNode) {
                String body = comment.path("body").asText("");
                int s = mockSentiment(body);
                if (s > 0) pos++;
                else if (s < 0) neg++;
                else neu++;
            }
            AnalysisSummary summary = new AnalysisSummary(total, pos, neg, neu);
            AnalysisReport report = new AnalysisReport(postId, "completed", summary, email, Instant.now().toString());
            analysisReports.put(postId, report);
            sendReportEmail(email, postId, summary);

            // Store analysis report into external entity service
            // Convert AnalysisReport to ObjectNode excluding technicalId
            ObjectNode analysisReportNode = objectMapper.valueToTree(report);

            // Add workflow function processcomment as required by new entityService.addItem signature
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                report,
                this::processcomment // workflow function applied asynchronously before persistence
            );

            // Optionally handle idFuture if needed (e.g., log result)
            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to add analysis report entity for postId={} due to {}", postId, ex.getMessage(), ex);
                } else {
                    logger.info("Successfully added analysis report entity with id={} for postId={}", id, postId);
                }
            });

        } catch (Exception ex) {
            updateReportWithError(postId, email, "Error during analysis: " + ex.getMessage());
        }
    }

    /**
     * Workflow function for 'comment' entity.
     * This function takes the entity data, modifies or processes it asynchronously before persistence,
     * and returns it back.
     *
     * IMPORTANT:
     * - You cannot add/update/delete entity of the same entityModel "comment" inside this method
     *   to avoid infinite recursion.
     *
     * @param entity the entity data object to process before persistence
     * @return processed entity data object (same type)
     */
    private Object processcomment(Object entity) {
        // Cast to AnalysisReport since that's the entity type we're adding
        if (entity instanceof AnalysisReport) {
            AnalysisReport report = (AnalysisReport) entity;
            // Example: update lastUpdated timestamp before saving
            report.setLastUpdated(Instant.now().toString());

            // You can add additional processing or enrich the report here
            // For example, log or modify some fields

            logger.debug("processcomment workflow applied on AnalysisReport with postId={}", report.getPostId());

            return report;
        }
        // If entity is not AnalysisReport, just return it unchanged
        return entity;
    }

    private void updateReportWithError(int postId, String email, String msg) {
        analysisReports.put(postId, new AnalysisReport(postId, "failed", null, email, Instant.now().toString()));
        logger.error("Report failed for postId={} error={}", postId, msg);
    }

    private int mockSentiment(String text) {
        // TODO replace with real sentiment analysis
        if (text.length() % 3 == 0) return 1;
        if (text.length() % 3 == 1) return -1;
        return 0;
    }

    private void sendReportEmail(String email, int postId, AnalysisSummary summary) {
        // TODO replace with real email logic
        logger.info("Sending email to {} for postId={} summary={}", email, postId, summary);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeRequest {
        @Min(1)
        private int postId;
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzeResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisReport {
        private Integer postId;
        private String analysisStatus;
        private AnalysisSummary summary;
        private String reportSentTo;
        private String lastUpdated;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisSummary {
        private int totalComments;
        private int positiveComments;
        private int negativeComments;
        private int neutralComments;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("Error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
            .body(new ErrorResponse(ex.getStatusCode().value(), ex.getReason()));
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
    }
}
```