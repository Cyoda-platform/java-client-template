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
                processDigestRequestJob(technicalId, technicalUUID, job);
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

    // Business logic: processDigestRequestJob
    private void processDigestRequestJob(String technicalId, UUID jobUuid, DigestRequestJob job) {
        logger.info("Processing DigestRequestJob {}", technicalId);
        try {
            job.setStatus("PROCESSING");
            // Update job status via TODO - no update support, so skip actual update
            // TODO: implement update operation via EntityService if available

            // Parse eventMetadata for API endpoints or use default
            String metadata = job.getEventMetadata();
            String petStatus = "available"; // default
            try {
                Map<String, Object> metadataMap = objectMapper.readValue(metadata, Map.class);
                if (metadataMap.containsKey("status")) {
                    petStatus = metadataMap.get("status").toString();
                }
            } catch (Exception e) {
                logger.error("Failed to parse eventMetadata for job {}: {}", technicalId, e.getMessage());
            }

            // Fetch data from petstore API /pet/findByStatus?status={petStatus}
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + petStatus;
            String apiResponse = "";
            try {
                apiResponse = restTemplate.getForObject(apiUrl, String.class);
            } catch (Exception e) {
                logger.error("Failed to fetch data from external API for job {}: {}", technicalId, e.getMessage());
                job.setStatus("FAILED");
                job.setCompletedAt(Instant.now().toString());
                // TODO: update job status via entityService - skip due to no update method
                return;
            }

            // Create DigestDataRecord
            DigestDataRecord dataRecord = new DigestDataRecord();
            dataRecord.setJobTechnicalId(technicalId);
            dataRecord.setApiEndpoint(apiUrl);
            dataRecord.setResponseData(apiResponse);
            dataRecord.setFetchedAt(Instant.now().toString());

            CompletableFuture<UUID> dataRecordIdFuture = entityService.addItem(
                    "DigestDataRecord",
                    ENTITY_VERSION,
                    dataRecord);
            UUID dataRecordUUID = dataRecordIdFuture.get();
            String dataRecordId = "data-" + digestDataRecordIdCounter.getAndIncrement();
            logger.info("Created DigestDataRecord {} for job {}", dataRecordId, technicalId);

            // Aggregate data into email content (simple HTML)
            String emailContent = "<html><body><h3>Petstore Digest</h3><pre>" + apiResponse + "</pre></body></html>";

            // Create DigestEmailRecord
            DigestEmailRecord emailRecord = new DigestEmailRecord();
            emailRecord.setJobTechnicalId(technicalId);
            emailRecord.setEmailContent(emailContent);
            emailRecord.setEmailStatus("PENDING");

            CompletableFuture<UUID> emailRecordIdFuture = entityService.addItem(
                    "DigestEmailRecord",
                    ENTITY_VERSION,
                    emailRecord);
            UUID emailRecordUUID = emailRecordIdFuture.get();
            String emailRecordId = "email-" + digestEmailRecordIdCounter.getAndIncrement();
            logger.info("Created DigestEmailRecord {} for job {}", emailRecordId, technicalId);

            // Send email (simulate)
            boolean emailSent = sendEmail(job.getUserEmail(), "Your Petstore Digest", emailContent);

            emailRecord.setEmailStatus(emailSent ? "SENT" : "FAILED");
            emailRecord.setEmailSentAt(Instant.now().toString());
            // TODO: update emailRecord via entityService - skip due to no update method

            // Update job status
            job.setStatus(emailSent ? "COMPLETED" : "FAILED");
            job.setCompletedAt(Instant.now().toString());
            // TODO: update job status via entityService - skip due to no update method

            logger.info("Completed processing DigestRequestJob {} with status {}", technicalId, job.getStatus());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution error in processDigestRequestJob {}: {}", technicalId, e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in processDigestRequestJob {}: {}", technicalId, e.getMessage());
        }
    }

    // Simulated email sending method
    private boolean sendEmail(String to, String subject, String content) {
        logger.info("Sending email to {} with subject {}", to, subject);
        // In real implementation, integrate with JavaMailSender or other email service
        // Here we simulate success
        return true;
    }
}