package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestDataRecord;
import com.java_template.application.entity.DigestEmailRecord;
import com.java_template.application.entity.DigestRequestJob;
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
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicLong digestRequestJobIdCounter = new AtomicLong(1);
    private final AtomicLong digestDataRecordIdCounter = new AtomicLong(1);
    private final AtomicLong digestEmailRecordIdCounter = new AtomicLong(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /controller/digestRequestJob - Create new DigestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob request) {
        try {
            if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
                logger.error("Invalid userEmail");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("userEmail is required and cannot be blank");
            }
            if (request.getEventMetadata() == null || request.getEventMetadata().isBlank()) {
                logger.error("Invalid eventMetadata");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("eventMetadata is required and cannot be blank");
            }

            // Prepare data to add via entityService
            DigestRequestJob job = new DigestRequestJob();
            job.setUserEmail(request.getUserEmail());
            job.setEventMetadata(request.getEventMetadata());
            job.setStatus("PENDING");
            job.setCreatedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    job);

            UUID technicalUUID = idFuture.get(); // wait for completion
            String technicalId = "job-" + digestRequestJobIdCounter.getAndIncrement();

            // Trigger processing
            try {
                // processDigestRequestJob(technicalId, technicalUUID, job); // extracted to workflow prototype
            } catch (Exception e) {
                logger.error("Error processing DigestRequestJob {}: {}", technicalId, e.getMessage());
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestRequestJob/{id} - Retrieve DigestRequestJob by technicalId
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String technicalId) {
        try {
            // Since we do not have direct mapping from technicalId string to UUID, 
            // here we try to find by condition on technicalId field equal to technicalId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", technicalId)
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    condition,
                    true);
            ArrayNode nodes = future.get();
            if (nodes == null || nodes.isEmpty()) {
                logger.error("DigestRequestJob not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestDataRecord/{id} - Retrieve DigestDataRecord by technicalId
    @GetMapping("/digestDataRecord/{id}")
    public ResponseEntity<?> getDigestDataRecord(@PathVariable("id") String technicalId) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", technicalId)
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    "DigestDataRecord",
                    ENTITY_VERSION,
                    condition,
                    true);
            ArrayNode nodes = future.get();
            if (nodes == null || nodes.isEmpty()) {
                logger.error("DigestDataRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestDataRecord not found");
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            DigestDataRecord record = objectMapper.treeToValue(node, DigestDataRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getDigestDataRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error in getDigestDataRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestEmailRecord/{id} - Retrieve DigestEmailRecord by technicalId
    @GetMapping("/digestEmailRecord/{id}")
    public ResponseEntity<?> getDigestEmailRecord(@PathVariable("id") String technicalId) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", technicalId)
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                    "DigestEmailRecord",
                    ENTITY_VERSION,
                    condition,
                    true);
            ArrayNode nodes = future.get();
            if (nodes == null || nodes.isEmpty()) {
                logger.error("DigestEmailRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmailRecord not found");
            }
            ObjectNode node = (ObjectNode) nodes.get(0);
            DigestEmailRecord record = objectMapper.treeToValue(node, DigestEmailRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getDigestEmailRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Internal error in getDigestEmailRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

}