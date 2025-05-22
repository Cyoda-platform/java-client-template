package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String ANALYZE_REQUEST_MODEL = "cyodaAnalyzeRequest";
    private static final String REPORT_MODEL = "cyodaReport";

    private final List<String> staticSubscribers = List.of(
            "user1@example.com",
            "user2@example.com"
    );

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class AnalyzeRequest {
        @NotBlank
        private String csvUrl;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResponse {
        private String taskId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResult {
        private String taskId;
        private String status; // completed | pending | failed
        private SummaryStatistics summaryStatistics;
        private BasicTrends basicTrends;
        private boolean emailSent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SummaryStatistics {
        private Double meanPrice;
        private Double medianPrice;
        private Integer totalListings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class BasicTrends {
        private String priceTrend;
    }

    @Data
    @AllArgsConstructor
    static class SubscribersResponse {
        private List<String> subscribers;
    }

    @PostMapping("/data/analyze")
    public CompletableFuture<ResponseEntity<AnalyzeResponse>> analyzeData(@RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received analyze request for CSV URL: {}", request.getCsvUrl());

        ObjectNode entity = objectMapper.createObjectNode();
        entity.put("csvUrl", request.getCsvUrl());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ANALYZE_REQUEST_MODEL,
                ENTITY_VERSION,
                entity
        );

        return idFuture.thenApply(id -> {
            logger.info("Analyze request accepted with id: {}", id);
            return ResponseEntity.ok(new AnalyzeResponse(id.toString(), "started"));
        }).exceptionally(ex -> {
            logger.error("Failed to add analyze request: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add analyze request");
        });
    }

    @GetMapping("/report/{taskId}")
    public CompletableFuture<ResponseEntity<ReportResult>> getReport(@PathVariable String taskId) {
        logger.info("Fetching report for taskId: {}", taskId);
        UUID uuid;
        try {
            uuid = UUID.fromString(taskId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid taskId format");
        }
        return entityService.getItem(REPORT_MODEL, ENTITY_VERSION, uuid)
                .thenApply(optionalEntity -> {
                    if (optionalEntity.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    ObjectNode reportNode = optionalEntity.get();
                    try {
                        return ResponseEntity.ok(objectMapper.treeToValue(reportNode, ReportResult.class));
                    } catch (Exception e) {
                        logger.error("Failed to deserialize report entity: {}", e.getMessage(), e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report");
                    }
                });
    }

    @GetMapping("/subscribers")
    public ResponseEntity<SubscribersResponse> getSubscribers() {
        logger.info("Returning static subscribers list");
        return ResponseEntity.ok(new SubscribersResponse(staticSubscribers));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}