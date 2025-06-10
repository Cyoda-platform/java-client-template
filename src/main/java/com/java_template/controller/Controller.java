package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService = new EntityService();

    @PostMapping("/comments/analyze")
    public ResponseEntity<String> analyzeComments(@RequestBody @Valid CommentRequest commentRequest) {
        int postId = commentRequest.getPostId();
        logger.info("Received request to analyze comments for postId: {}", postId);

        try {
            // Fetch comments using EntityService
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.postId", "EQUALS", postId));
            CompletableFuture<ArrayNode> commentsFuture = entityService.getItemsByCondition("comments", ENTITY_VERSION, condition);
            ArrayNode comments = commentsFuture.join();

            // Prepare report object
            ObjectNode report = objectMapper.createObjectNode();
            report.put("reportId", "report-" + postId);
            report.put("postId", postId);

            // Save report using EntityService without workflow
            entityService.addItem("report", ENTITY_VERSION, report).join();

            return ResponseEntity.ok("Comments analyzed and report generation initiated.");
        } catch (Exception e) {
            logger.error("Error fetching or analyzing comments", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid postId.");
        }
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<Report> getReport(@PathVariable @NotBlank String reportId) {
        logger.info("Fetching report with ID: {}", reportId);

        try {
            // Fetch report using EntityService
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.reportId", "EQUALS", reportId));
            CompletableFuture<ArrayNode> reportsFuture = entityService.getItemsByCondition("report", ENTITY_VERSION, condition);
            ArrayNode reports = reportsFuture.join();

            if (reports.size() > 0) {
                JsonNode reportNode = reports.get(0);
                Report report = objectMapper.treeToValue(reportNode, Report.class);
                return ResponseEntity.ok(report);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found.");
            }
        } catch (Exception e) {
            logger.error("Error fetching report", e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling exception: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("status", "error", "message", ex.getStatusCode().toString()));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class CommentRequest {
        @Min(1)
        private int postId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Report {
        private String reportId;
        private int postId;
        private String analysisSummary;
        private String[] keywords;
        private double sentimentScore;
    }
}
