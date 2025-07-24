package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.Report;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST /entity/reportJob - create new ReportJob, trigger processing
    @PostMapping(path = "/reportJob", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReportJob() {
        try {
            // Prepare new ReportJob entity for storage
            ReportJob newJob = new ReportJob();
            newJob.setRequestedAt(java.time.LocalDateTime.now());
            newJob.setStatus("PENDING");

            // Add new ReportJob via EntityService, get technicalId (UUID)
            CompletableFuture<UUID> idFuture = entityService.addItem("ReportJob", ENTITY_VERSION, newJob);
            UUID technicalId = idFuture.join();
            String technicalIdStr = technicalId.toString();

            logger.info("Created ReportJob with ID: {}", technicalIdStr);

            // After creation, update entity with technicalId for consistency if needed (skipped - no update allowed currently)

            return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument creating ReportJob", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error creating ReportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/reportJob/{id} - retrieve ReportJob by technicalId (UUID)
    @GetMapping(path = "/reportJob/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportJobById(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ReportJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("ReportJob not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "ReportJob not found"));
            }
            // Deserialize ObjectNode to ReportJob entity for returning
            ReportJob job = objectMapper.treeToValue(node, ReportJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid UUID format for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", "Invalid UUID format"));
        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON for ReportJob with ID: {}", id, ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving ReportJob with ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/report/{id} - retrieve Report by ReportJob technicalId (UUID)
    @GetMapping(path = "/report/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportByJobId(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            // Use condition to filter reports by jobTechnicalId field equal to id (string)
            Condition condition = Condition.of("$.jobTechnicalId", "EQUALS", id);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Report", ENTITY_VERSION, searchCondition, true);
            ArrayNode reportsNode = filteredItemsFuture.join();

            if (reportsNode == null || reportsNode.isEmpty()) {
                logger.error("Report not found for ReportJob ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "Report not found"));
            }
            // Assuming one report per jobTechnicalId, return the first found
            ObjectNode reportNode = (ObjectNode) reportsNode.get(0);
            Report report = objectMapper.treeToValue(reportNode, Report.class);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument retrieving Report for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", ex.getMessage()));
        } catch (JsonProcessingException ex) {
            logger.error("Error processing JSON for Report with ReportJob ID: {}", id, ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving Report for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", "Internal server error"));
        }
    }
}