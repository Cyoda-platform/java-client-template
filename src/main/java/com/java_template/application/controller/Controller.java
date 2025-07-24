package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.EmailDispatchRecord;
import com.java_template.application.entity.ExternalApiData;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST /entity/digest-request-jobs - create a new digest request job
    @PostMapping("/digest-request-jobs")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob request) {
        try {
            // Validate required fields
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                logger.error("Email is blank or missing");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
            }
            if (!request.isValid()) {
                logger.error("DigestRequestJob entity validation failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid DigestRequestJob entity"));
            }
            // Prepare entity for saving
            request.setStatus("PENDING");

            // Add item to external service, get technicalId as UUID
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    request
            );
            UUID technicalUuid = idFuture.get();
            String technicalId = technicalUuid.toString();

            logger.info("Created DigestRequestJob with technicalId: {}", technicalId);

            // Return technicalId only
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating DigestRequestJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
    }

    // GET /entity/digest-request-jobs/{id} - retrieve job by technicalId
    @GetMapping("/digest-request-jobs/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalUuid = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    technicalUuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestRequestJob not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException iae) {
            logger.error("Invalid UUID format for id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            logger.error("Execution exception when retrieving DigestRequestJob: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        } catch (Exception e) {
            logger.error("Error retrieving DigestRequestJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
    }

    // GET /entity/external-api-data/{jobTechnicalId} - retrieve external API data by jobTechnicalId
    @GetMapping("/external-api-data/{jobTechnicalId}")
    public ResponseEntity<?> getExternalApiData(@PathVariable String jobTechnicalId) throws JsonProcessingException {
        try {
            // We want to find ExternalApiData entity where jobTechnicalId field equals jobTechnicalId param
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.jobTechnicalId", "EQUALS", jobTechnicalId)
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    "ExternalApiData",
                    ENTITY_VERSION,
                    condition,
                    true
            );
            ArrayNode nodes = filteredItemsFuture.get();
            if (nodes == null || nodes.isEmpty()) {
                logger.error("ExternalApiData not found for jobTechnicalId: {}", jobTechnicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Take first match
            ObjectNode node = (ObjectNode) nodes.get(0);
            ExternalApiData data = objectMapper.treeToValue(node, ExternalApiData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving ExternalApiData: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
    }

    // GET /entity/email-dispatch-record/{jobTechnicalId} - retrieve email dispatch status by jobTechnicalId
    @GetMapping("/email-dispatch-record/{jobTechnicalId}")
    public ResponseEntity<?> getEmailDispatchRecord(@PathVariable String jobTechnicalId) throws JsonProcessingException {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.jobTechnicalId", "EQUALS", jobTechnicalId)
            );
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    "EmailDispatchRecord",
                    ENTITY_VERSION,
                    condition,
                    true
            );
            ArrayNode nodes = filteredItemsFuture.get();
            if (nodes == null || nodes.isEmpty()) {
                logger.error("EmailDispatchRecord not found for jobTechnicalId: {}", jobTechnicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            EmailDispatchRecord record = objectMapper.treeToValue(node, EmailDispatchRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving EmailDispatchRecord: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
    }
}