package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
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
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /entity/digestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<Map<String, String>> createDigestRequestJob(@RequestBody DigestRequestJob job) {
        try {
            logger.info("Received request to create DigestRequestJob");

            if (job == null) {
                logger.error("DigestRequestJob payload is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
            }

            // Set initial status and createdAt if missing
            if (job.getStatus() == null || job.getStatus().isBlank()) {
                job.setStatus("PENDING");
            }
            if (job.getCreatedAt() == null) {
                job.setCreatedAt(Instant.now());
            }

            if (!job.isValid()) {
                logger.error("DigestRequestJob validation failed");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid DigestRequestJob fields"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    job
            );
            UUID technicalId = idFuture.get();
            String technicalIdStr = technicalId.toString();

            logger.info("DigestRequestJob created with ID: {}", technicalIdStr);

            processDigestRequestJob(technicalIdStr, job);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/digestRequestJob/{id}
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<DigestRequestJob> getDigestRequestJob(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for DigestRequestJob ID: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestRequestJob not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            DigestRequestJob job = node.traverse().readValueAs(DigestRequestJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            logger.error("Error in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // GET /entity/externalApiData/{id}
    @GetMapping("/externalApiData/{id}")
    public ResponseEntity<ExternalApiData> getExternalApiData(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for ExternalApiData ID: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "ExternalApiData",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("ExternalApiData not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            ExternalApiData data = node.traverse().readValueAs(ExternalApiData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getExternalApiData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            logger.error("Error in getExternalApiData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // GET /entity/digestEmail/{id}
    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<DigestEmail> getDigestEmail(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for DigestEmail ID: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "DigestEmail",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestEmail not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            DigestEmail email = node.traverse().readValueAs(DigestEmail.class);
            return ResponseEntity.ok(email);
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getDigestEmail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            logger.error("Error in getDigestEmail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private void processDigestRequestJob(String technicalId, DigestRequestJob job) {
        logger.info("Processing DigestRequestJob with ID: {}", technicalId);

        try {
            // Step 1: Validation (already done on input, but double-check)
            if (!job.isValid()) {
                logger.error("DigestRequestJob {} validation failed during processing", technicalId);
                job.setStatus("FAILED");
                updateDigestRequestJobStatus(technicalId, job);
                return;
            }

            job.setStatus("PROCESSING");
            updateDigestRequestJobStatus(technicalId, job);

            // Step 2: Call external API
            String endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            if (job.getRequestMetadata() != null && !job.getRequestMetadata().isBlank()) {
                String metadata = job.getRequestMetadata().trim();
                if (metadata.startsWith("status=")) {
                    String statusValue = metadata.substring(7);
                    endpoint = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusValue;
                }
            }

            ExternalApiData apiData = new ExternalApiData();
            apiData.setJobTechnicalId(technicalId);
            apiData.setApiEndpoint(endpoint);
            apiData.setFetchedAt(Instant.now());

            logger.info("Calling external API at {}", endpoint);
            String response = restTemplate.getForObject(endpoint, String.class);
            apiData.setResponseData(response);

            if (!apiData.isValid()) {
                throw new RuntimeException("ExternalApiData validation failed");
            }

            CompletableFuture<UUID> apiDataIdFuture = entityService.addItem(
                    "ExternalApiData",
                    ENTITY_VERSION,
                    apiData
            );
            UUID apiDataId = apiDataIdFuture.get();
            logger.info("ExternalApiData saved with ID: {}", apiDataId.toString());

            // Step 3: Compile digest email content
            String emailContent = compileDigestContent(response);

            DigestEmail digestEmail = new DigestEmail();
            digestEmail.setJobTechnicalId(technicalId);
            digestEmail.setEmailContent(emailContent);
            digestEmail.setSentAt(null);
            digestEmail.setDeliveryStatus("PENDING");

            if (!digestEmail.isValid()) {
                throw new RuntimeException("DigestEmail validation failed");
            }

            CompletableFuture<UUID> digestEmailIdFuture = entityService.addItem(
                    "DigestEmail",
                    ENTITY_VERSION,
                    digestEmail
            );
            UUID digestEmailId = digestEmailIdFuture.get();
            String digestEmailIdStr = digestEmailId.toString();
            logger.info("DigestEmail saved with ID: {}", digestEmailIdStr);

            // Step 4: Send email
            boolean emailSent = sendEmail(job.getEmail(), emailContent);
            if (emailSent) {
                digestEmail.setSentAt(Instant.now());
                digestEmail.setDeliveryStatus("SENT");
                logger.info("Email sent successfully to {}", job.getEmail());
            } else {
                digestEmail.setDeliveryStatus("FAILED");
                logger.error("Failed to send email to {}", job.getEmail());
            }

            // TODO: Update DigestEmail entity with sentAt and deliveryStatus (update operation not supported by EntityService)
            // For now, skipping update.

            // Step 5: Finalize job status
            job.setStatus(emailSent ? "COMPLETED" : "FAILED");
            updateDigestRequestJobStatus(technicalId, job);

        } catch (Exception e) {
            logger.error("Error processing DigestRequestJob {}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            try {
                updateDigestRequestJobStatus(technicalId, job);
            } catch (Exception ex) {
                logger.error("Error updating DigestRequestJob status to FAILED for ID {}: {}", technicalId, ex.getMessage());
            }
        }
    }

    private void updateDigestRequestJobStatus(String technicalId, DigestRequestJob job) throws ExecutionException, InterruptedException {
        // TODO: Update operation is not supported by EntityService, skipping update.
        // This is a placeholder to note that update is needed here.
    }

    private String compileDigestContent(String apiResponse) {
        if (apiResponse == null || apiResponse.isBlank()) {
            return "<html><body><p>No data available for digest.</p></body></html>";
        }
        return "<html><body><h3>Your Digest</h3><pre>" + apiResponse + "</pre></body></html>";
    }

    private boolean sendEmail(String recipientEmail, String content) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            logger.error("Recipient email is blank, cannot send email");
            return false;
        }
        logger.info("Simulating sending email to: {}", recipientEmail);
        // In real implementation, integrate with email service here
        return true;
    }
}