package com.java_template.application.controller;

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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

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
            request.setCreatedAt(Instant.now().toString());

            // Add item to external service, get technicalId as UUID
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    request
            );
            UUID technicalUuid = idFuture.get();
            String technicalId = technicalUuid.toString();

            logger.info("Created DigestRequestJob with technicalId: {}", technicalId);

            // Trigger processing asynchronously
            processDigestRequestJob(technicalId, request);

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
    public ResponseEntity<?> getDigestRequestJob(@PathVariable String id) {
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
            DigestRequestJob job = node.traverse().readValueAs(DigestRequestJob.class);
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
    public ResponseEntity<?> getExternalApiData(@PathVariable String jobTechnicalId) {
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
            ExternalApiData data = node.traverse().readValueAs(ExternalApiData.class);
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
    public ResponseEntity<?> getEmailDispatchRecord(@PathVariable String jobTechnicalId) {
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
            EmailDispatchRecord record = node.traverse().readValueAs(EmailDispatchRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving EmailDispatchRecord: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        }
    }

    // Business logic for processing DigestRequestJob
    private void processDigestRequestJob(String technicalId, DigestRequestJob job) {
        logger.info("Processing DigestRequestJob with ID: {}", technicalId);

        // Validate email format (simple check)
        if (job.getEmail().isBlank() || !job.getEmail().contains("@")) {
            logger.error("Invalid email format for job ID: {}", technicalId);
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            // TODO: update job status in EntityService - skipping update as per instruction
            return;
        }

        // Retrieve data from external API
        String apiDataPayload = "";
        try {
            apiDataPayload = fetchExternalApiData();
        } catch (Exception e) {
            logger.error("Failed to fetch external API data for job ID: {} - {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            // TODO: update job status in EntityService - skipping update as per instruction
            return;
        }

        // Save ExternalApiData entity
        ExternalApiData apiData = new ExternalApiData();
        apiData.setJobTechnicalId(technicalId);
        apiData.setDataPayload(apiDataPayload);
        apiData.setRetrievedAt(Instant.now().toString());
        try {
            entityService.addItem("ExternalApiData", ENTITY_VERSION, apiData).get();
            logger.info("Saved ExternalApiData for job ID: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to save ExternalApiData for job ID: {} - {}", technicalId, e.getMessage());
            // Continue processing despite failure to save API data
        }

        // Proceed with email dispatch
        EmailDispatchRecord emailRecord = new EmailDispatchRecord();
        emailRecord.setJobTechnicalId(technicalId);
        emailRecord.setEmail(job.getEmail());
        emailRecord.setDispatchStatus("PENDING");
        try {
            entityService.addItem("EmailDispatchRecord", ENTITY_VERSION, emailRecord).get();
            logger.info("Created EmailDispatchRecord for job ID: {}", technicalId);
        } catch (Exception e) {
            logger.error("Failed to create EmailDispatchRecord for job ID: {} - {}", technicalId, e.getMessage());
            // Continue processing despite failure to save email record
        }

        processEmailDispatchRecord(technicalId, emailRecord);

        // Update job status to COMPLETED
        job.setStatus("COMPLETED");
        job.setCompletedAt(Instant.now().toString());
        // TODO: update job status in EntityService - skipping update as per instruction
        logger.info("DigestRequestJob with ID: {} completed successfully", technicalId);
    }

    // Fetch data from external API (petstore.swagger.io)
    private String fetchExternalApiData() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        // Example endpoint: GET /pet/findByStatus?status=available
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=available"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("External API call failed with status: " + response.statusCode());
        }
        return response.body();
    }

    // Business logic for processing EmailDispatchRecord
    private void processEmailDispatchRecord(String technicalId, EmailDispatchRecord record) {
        logger.info("Processing EmailDispatchRecord with job ID: {}", technicalId);

        // Simulate email sending (in real app, integrate with email service)
        try {
            // Simulate sending delay
            Thread.sleep(500);
            // Mark as SENT
            record.setDispatchStatus("SENT");
            record.setSentAt(Instant.now().toString());
            // TODO: update email dispatch record in EntityService - skipping update as per instruction
            logger.info("Email sent successfully to {} for job ID: {}", record.getEmail(), technicalId);
        } catch (InterruptedException e) {
            logger.error("Email sending interrupted for job ID: {}", technicalId);
            record.setDispatchStatus("FAILED");
            // TODO: update email dispatch record in EntityService - skipping update as per instruction
        }
    }
}