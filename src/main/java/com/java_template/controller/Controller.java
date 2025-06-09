package com.java_template.controller;

import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Inject ObjectMapper via constructor
    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/data/analyze")
    public ResponseEntity<Map<String, String>> analyzeData(@RequestBody @Valid DataAnalysisRequest request) {
        String csvUrl = request.getCsvUrl();
        logger.info("Starting data analysis for URL: {}", csvUrl);

        ObjectNode reportData = JsonNodeFactory.instance.objectNode();
        reportData.put("content", "Initial Report Content");
        reportData.put("generatedAt", new Date().getTime());

        entityService.addItem("Report", ENTITY_VERSION, reportData)
            .thenAccept(technicalId -> logger.info("Data analysis completed for URL: {}, technicalId: {}", csvUrl, technicalId))
            .exceptionally(ex -> {
                logger.error("Error during data analysis: {}", ex.getMessage());
                return null;
            });

        return ResponseEntity.ok(Collections.singletonMap("message", "Data analysis started."));
    }

    @PostMapping("/report/send")
    public ResponseEntity<Map<String, String>> sendReport(@RequestBody @Valid ReportRequest reportRequest) {
        logger.info("Sending report to subscribers: {}", reportRequest.getSubscribers());

        entityService.getItemsByCondition("Report", ENTITY_VERSION,
                SearchConditionRequest.group("AND", Condition.of("$.content", "IS_NULL", null)))
            .thenAccept(reports -> reports.forEach(report ->
                logger.info("Report sent to subscribers: {}", reportRequest.getSubscribers())))
            .exceptionally(ex -> {
                logger.error("Error sending reports: {}", ex.getMessage());
                return null;
            });

        return ResponseEntity.ok(Collections.singletonMap("message", "Report sending initiated."));
    }

    @GetMapping("/report")
    public ResponseEntity<Report> getReport() {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Report", ENTITY_VERSION, UUID.randomUUID()); // Replace UUID.randomUUID() with actual ID
        Report report;
        try {
            report = itemFuture.thenApply(itemNode -> {
                if (itemNode == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No report found");
                }
                return new Report(itemNode.get("content").asText(), new Date(itemNode.get("generatedAt").asLong())); // Convert ObjectNode to Report
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error fetching report: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching report");
        }

        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", ex.getStatusCode().toString());
        errorResponse.put("message", ex.getMessage());
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Report {
        private String content;
        private Date generatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReportRequest {
        @NotBlank
        private String reportFormat;
        @Size(min = 1)
        @Email
        private List<String> subscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class DataAnalysisRequest {
        @NotBlank
        private String csvUrl;
    }
}