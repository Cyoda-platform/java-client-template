package com.java_template.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-comments")
@Validated
@Slf4j
public class Controller {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;
    private static final String ENTITY_NAME = "comment";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        log.info("Controller initialized");
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyzeComments(@RequestBody @Valid AnalyzeRequest request) {
        log.info("Received analyze request for postId={} and email={}", request.getPostId(), request.getEmail());

        try {
            ObjectNode entityNode = objectMapper.createObjectNode();
            entityNode.put("postId", request.getPostId());
            entityNode.put("analysisStatus", "processing");
            entityNode.put("reportSentTo", request.getEmail());
            entityNode.put("lastUpdated", Instant.now().toString());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode
            );

            AnalyzeResponse response = new AnalyzeResponse(
                "processing",
                "Analysis started for postId " + request.getPostId() + ", report will be sent to " + request.getEmail()
            );
            return ResponseEntity.accepted().body(response);
        } catch (Exception ex) {
            log.error("Failed to submit analysis request", ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to start analysis");
        }
    }

    @GetMapping("/report/{postId}")
    public ResponseEntity<AnalysisReport> getReport(@PathVariable @Min(1) Integer postId) {
        log.info("Fetching report for postId={}", postId);
        try {
            CompletableFuture<ObjectNode> entityFuture = entityService.getItemByField(ENTITY_NAME, "postId", postId);
            ObjectNode entityNode = entityFuture.get();

            if (entityNode == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Report not found for postId " + postId);
            }

            AnalysisReport report = convertEntityNodeToAnalysisReport(entityNode);
            return ResponseEntity.ok(report);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch report for postId {}", postId, ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Failed to fetch report");
        }
    }

    private AnalysisReport convertEntityNodeToAnalysisReport(ObjectNode entityNode) {
        try {
            Integer postId = entityNode.has("postId") && !entityNode.get("postId").isNull() ? entityNode.get("postId").asInt() : null;
            String status = entityNode.has("analysisStatus") && !entityNode.get("analysisStatus").isNull() ? entityNode.get("analysisStatus").asText() : null;
            String reportSentTo = entityNode.has("reportSentTo") && !entityNode.get("reportSentTo").isNull() ? entityNode.get("reportSentTo").asText() : null;
            String lastUpdated = entityNode.has("lastUpdated") && !entityNode.get("lastUpdated").isNull() ? entityNode.get("lastUpdated").asText() : null;

            AnalysisSummary summary = null;
            if (entityNode.has("summary") && entityNode.get("summary").isObject()) {
                JsonNode summaryNode = entityNode.get("summary");
                int total = summaryNode.has("totalComments") ? summaryNode.get("totalComments").asInt(0) : 0;
                int positive = summaryNode.has("positiveComments") ? summaryNode.get("positiveComments").asInt(0) : 0;
                int negative = summaryNode.has("negativeComments") ? summaryNode.get("negativeComments").asInt(0) : 0;
                int neutral = summaryNode.has("neutralComments") ? summaryNode.get("neutralComments").asInt(0) : 0;
                summary = new AnalysisSummary(total, positive, negative, neutral);
            }
            return new AnalysisReport(postId, status, summary, reportSentTo, lastUpdated);
        } catch (Exception ex) {
            log.error("Failed to convert entityNode to AnalysisReport", ex);
            return null;
        }
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
        log.error("Error: {}", ex.getReason());
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