package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicLong reportJobIdCounter = new AtomicLong(1);

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /entity/reportJob - create ReportJob and trigger processing
    @PostMapping("/reportJob")
    public ResponseEntity<?> createReportJob(@RequestBody Map<String, String> request) {
        try {
            String recipientEmail = request.get("recipientEmail");
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("recipientEmail is missing or blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "recipientEmail is required"));
            }

            ReportJob job = new ReportJob();
            // Generate unique technicalId as String from AtomicLong
            String technicalId = Long.toString(reportJobIdCounter.getAndIncrement());
            job.setId(technicalId);
            job.setRequestTimestamp(LocalDateTime.now());
            job.setStatus("PENDING");
            job.setRecipientEmail(recipientEmail);
            job.setErrorMessage(null);

            // Save job via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "ReportJob",
                    ENTITY_VERSION,
                    job
            );
            UUID technicalUUID = idFuture.get(); // block to ensure saved, though async ideally

            log.info("Created ReportJob with ID: {}", technicalId);

            // Trigger processing asynchronously (simulated here synchronously)
            // processReportJob removed

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createReportJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error creating ReportJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/reportJob/{id} - get ReportJob by technicalId
    @GetMapping("/reportJob/{id}")
    public ResponseEntity<?> getReportJob(@PathVariable("id") String id) {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                // If id is not UUID, try to find by scanning all ReportJobs with matching id field
                // Because original prototype used String ids like "1", "2", etc.
                // So we attempt a custom search here
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", id));
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                        "ReportJob", ENTITY_VERSION, condition, true);
                ArrayNode nodes = itemsFuture.get();
                if (nodes == null || nodes.isEmpty()) {
                    log.error("ReportJob with ID {} not found", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
                }
                ObjectNode node = (ObjectNode) nodes.get(0);
                return ResponseEntity.ok(node);
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "ReportJob", ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("ReportJob with ID {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getReportJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving ReportJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/conversionReport/{jobTechnicalId} - get ConversionReport by jobTechnicalId
    @GetMapping("/conversionReport/{jobTechnicalId}")
    public ResponseEntity<?> getConversionReport(@PathVariable("jobTechnicalId") String jobTechnicalId) {
        try {
            // Search ConversionReport by jobTechnicalId field
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.jobTechnicalId", "EQUALS", jobTechnicalId));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    "ConversionReport", ENTITY_VERSION, condition, true);
            ArrayNode nodes = itemsFuture.get();
            if (nodes == null || nodes.isEmpty()) {
                log.error("ConversionReport for Job ID {} not found", jobTechnicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ConversionReport not found"));
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getConversionReport: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving ConversionReport: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportJob {
        private String id;
        private LocalDateTime requestTimestamp;
        private String status;
        private String recipientEmail;
        private String errorMessage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionReport {
        private String jobTechnicalId;
        private LocalDateTime createdTimestamp;
        private BigDecimal btcUsdRate;
        private BigDecimal btcEurRate;
        private LocalDateTime emailSentTimestamp;
        private String status;
    }
}