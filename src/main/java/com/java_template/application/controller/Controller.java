package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // POST /entity/reportJob - create ReportJob and trigger processing
    @PostMapping("/reportJob")
    public ResponseEntity<?> createReportJob(@RequestBody Map<String, String> request) throws ExecutionException, InterruptedException, JsonProcessingException {
        String recipientEmail = request.get("recipientEmail");
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "recipientEmail is required"));
        }

        // Create ReportJob entity
        com.java_template.application.entity.ReportJob job = new com.java_template.application.entity.ReportJob();
        job.setRequestTimestamp(LocalDateTime.now());
        job.setStatus("PENDING");
        job.setRecipientEmail(recipientEmail);
        job.setErrorMessage(null);

        // Save job via entityService
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "reportJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalUUID = idFuture.get();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalUUID.toString()));
    }

    // GET /entity/reportJob/{id} - get ReportJob by technicalId
    @GetMapping("/reportJob/{id}")
    public ResponseEntity<?> getReportJob(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalUUID;
        try {
            technicalUUID = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            // If id is not UUID, try to find by scanning all ReportJobs with matching id field
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", id));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    "reportJob", ENTITY_VERSION, condition, true);
            ArrayNode nodes = itemsFuture.get();
            if (nodes == null || nodes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            return ResponseEntity.ok(node);
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "reportJob", ENTITY_VERSION, technicalUUID);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
        }
        return ResponseEntity.ok(node);
    }

    // GET /entity/conversionReport/{jobTechnicalId} - get ConversionReport by jobTechnicalId
    @GetMapping("/conversionReport/{jobTechnicalId}")
    public ResponseEntity<?> getConversionReport(@PathVariable("jobTechnicalId") String jobTechnicalId) throws ExecutionException, InterruptedException {
        // Search ConversionReport by jobTechnicalId field
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.jobTechnicalId", "EQUALS", jobTechnicalId));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                "conversionReport", ENTITY_VERSION, condition, true);
        ArrayNode nodes = itemsFuture.get();
        if (nodes == null || nodes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ConversionReport not found"));
        }
        ObjectNode node = (ObjectNode) nodes.get(0);
        return ResponseEntity.ok(node);
    }
}