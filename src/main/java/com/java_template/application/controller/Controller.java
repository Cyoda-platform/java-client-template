package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
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
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // POST /entity/digestRequests
    @PostMapping("/digestRequests")
    public ResponseEntity<?> createDigestRequest(@RequestBody DigestRequest request) {
        try {
            if (request == null) {
                logger.error("Received null DigestRequest");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
            }
            if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "userEmail is required"));
            }
            if (request.getExternalApiEndpoint() == null || request.getExternalApiEndpoint().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "externalApiEndpoint is required"));
            }

            request.setRequestTimestamp(Instant.now());
            request.setStatus("PENDING");

            // Persist DigestRequest using entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequest",
                    ENTITY_VERSION,
                    request
            );
            UUID technicalIdUuid = idFuture.get();
            String technicalId = technicalIdUuid.toString();
            logger.info("Created DigestRequest with technicalId: {}", technicalId);

            // Trigger processing in a new thread to simulate async event-driven processing
            new Thread(() -> processDigestRequest(technicalId, request)).start();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/digestRequests/{id}
    @GetMapping("/digestRequests/{id}")
    public ResponseEntity<?> getDigestRequest(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "DigestRequest",
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestRequest not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error in getDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/digestData/{id}
    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "DigestData",
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestData not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getDigestData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error in getDigestData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/emailDispatch/{id}
    @GetMapping("/emailDispatch/{id}")
    public ResponseEntity<?> getEmailDispatch(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "EmailDispatch",
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("EmailDispatch not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailDispatch not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getEmailDispatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error in getEmailDispatch: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Process methods
    private void processDigestRequest(String technicalId, DigestRequest request) {
        logger.info("Processing DigestRequest with id: {}", technicalId);
        try {
            // Validate again for safety
            if (!request.isValid()) {
                logger.error("DigestRequest {} validation failed", technicalId);
                request.setStatus("FAILED");
                // todo: update operation not supported, skipping update
                return;
            }
            request.setStatus("PROCESSING");
            // todo: update operation not supported, skipping update

            // Retrieve data from external API (Petstore Swagger API)
            String baseUrl = "https://petstore.swagger.io/v2";
            String endpoint = request.getExternalApiEndpoint();
            String url = baseUrl + endpoint;

            logger.info("Calling external API: {}", url);

            var response = restTemplate.getForEntity(url, String.class);
            String apiResponse = response.getBody();

            if (apiResponse == null || apiResponse.isBlank()) {
                logger.error("Empty response from external API for DigestRequest {}", technicalId);
                request.setStatus("FAILED");
                // todo: update operation not supported, skipping update
                return;
            }

            // Persist DigestData
            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(technicalId);
            digestData.setRetrievedData(apiResponse);
            digestData.setProcessedTimestamp(Instant.now());

            CompletableFuture<UUID> digestDataIdFuture = entityService.addItem(
                    "DigestData",
                    ENTITY_VERSION,
                    digestData
            );
            UUID digestDataId = digestDataIdFuture.get();
            logger.info("Persisted DigestData with id: {}", digestDataId.toString());

            // Update DigestRequest status to COMPLETED
            request.setStatus("COMPLETED");
            // todo: update operation not supported, skipping update

            // Trigger email dispatch process
            processEmailDispatch(technicalId, request, digestData);

        } catch (Exception e) {
            logger.error("Error while retrieving data for DigestRequest {}: {}", technicalId, e.getMessage());
            request.setStatus("FAILED");
            // todo: update operation not supported, skipping update
        }
    }

    private void processEmailDispatch(String digestRequestId, DigestRequest request, DigestData digestData) {
        logger.info("Processing EmailDispatch for DigestRequest id: {}", digestRequestId);

        EmailDispatch emailDispatch = new EmailDispatch();

        emailDispatch.setDigestRequestId(digestRequestId);

        // Compose email content (simple HTML format)
        String emailContent = "<html><body><h3>Digest Data</h3><pre>" + digestData.getRetrievedData() + "</pre></body></html>";

        emailDispatch.setEmailContent(emailContent);
        emailDispatch.setDispatchTimestamp(Instant.now());
        emailDispatch.setStatus("PENDING");

        try {
            CompletableFuture<UUID> emailDispatchIdFuture = entityService.addItem(
                    "EmailDispatch",
                    ENTITY_VERSION,
                    emailDispatch
            );
            UUID emailDispatchId = emailDispatchIdFuture.get();
            logger.info("Persisted EmailDispatch with id: {}", emailDispatchId.toString());

            // Simulate sending email
            logger.info("Sending email to {}", request.getUserEmail());
            Thread.sleep(500);

            emailDispatch.setStatus("SENT");
            // todo: update operation not supported, skipping update
            logger.info("Email sent successfully for DigestRequest id: {}", digestRequestId);

        } catch (Exception e) {
            logger.error("Failed to send email for DigestRequest {}: {}", digestRequestId, e.getMessage());
            emailDispatch.setStatus("FAILED");
            // todo: update operation not supported, skipping update
        }
    }
}