package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.EmailReport;
import com.java_template.application.entity.Report;
import com.java_template.application.entity.ReportJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@AllArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /entity/reportJob - create ReportJob entity
    @PostMapping("/reportJob")
    public ResponseEntity<Map<String, String>> createReportJob(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            ReportJob reportJob = new ReportJob();

            // Initialize with default or empty values as request body is empty/optional for prototype
            reportJob.setBtcUsdRate(BigDecimal.ZERO);
            reportJob.setBtcEurRate(BigDecimal.ZERO);
            reportJob.setTimestamp(OffsetDateTime.now());
            reportJob.setEmailStatus("PENDING");

            // Use entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem("ReportJob", ENTITY_VERSION, reportJob);
            UUID technicalId = idFuture.get();

            String technicalIdStr = technicalId.toString();

            // processReportJob method removed for extraction

            logger.info("Created ReportJob with id {}", technicalIdStr);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to create ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /entity/reportJob/{id} - retrieve ReportJob by technicalId
    @GetMapping("/reportJob/{id}")
    public ResponseEntity<ReportJob> getReportJob(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ReportJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("ReportJob not found with id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Map ObjectNode to ReportJob
            ReportJob reportJob = mapObjectNodeToReportJob(node);
            return ResponseEntity.ok(reportJob);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ReportJob id {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve ReportJob with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving ReportJob with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /entity/report/{id} - retrieve Report by technicalId
    @GetMapping("/report/{id}")
    public ResponseEntity<Report> getReport(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Report", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Report not found with id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Report report = mapObjectNodeToReport(node);
            return ResponseEntity.ok(report);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Report id {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve Report with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving Report with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // No POST for EmailReport creation as it's triggered internally by processing ReportJob

    // Helper method to map ObjectNode to ReportJob
    private ReportJob mapObjectNodeToReportJob(ObjectNode node) {
        ReportJob reportJob = new ReportJob();
        try {
            if (node.has("btcUsdRate")) {
                reportJob.setBtcUsdRate(new BigDecimal(node.get("btcUsdRate").asText()));
            }
            if (node.has("btcEurRate")) {
                reportJob.setBtcEurRate(new BigDecimal(node.get("btcEurRate").asText()));
            }
            if (node.has("timestamp")) {
                reportJob.setTimestamp(OffsetDateTime.parse(node.get("timestamp").asText()));
            }
            if (node.has("emailStatus")) {
                reportJob.setEmailStatus(node.get("emailStatus").asText());
            }
        } catch (Exception e) {
            logger.warn("Error mapping ReportJob fields from ObjectNode", e);
        }
        return reportJob;
    }

    // Helper method to map ObjectNode to Report
    private Report mapObjectNodeToReport(ObjectNode node) {
        Report report = new Report();
        try {
            if (node.has("reportJobId")) {
                report.setReportJobId(node.get("reportJobId").asText());
            }
            if (node.has("btcUsdRate")) {
                report.setBtcUsdRate(new BigDecimal(node.get("btcUsdRate").asText()));
            }
            if (node.has("btcEurRate")) {
                report.setBtcEurRate(new BigDecimal(node.get("btcEurRate").asText()));
            }
            if (node.has("timestamp")) {
                report.setTimestamp(OffsetDateTime.parse(node.get("timestamp").asText()));
            }
        } catch (Exception e) {
            logger.warn("Error mapping Report fields from ObjectNode", e);
        }
        return report;
    }
}