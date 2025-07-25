package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/digest")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final String ENTITY_DIGEST_REQUEST = "DigestRequest";
    private static final String ENTITY_DIGEST_DATA = "DigestData";
    private static final String ENTITY_DIGEST_EMAIL = "DigestEmail";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // POST /digest/digestRequests - create DigestRequest
    @PostMapping("/digestRequests")
    public ResponseEntity<?> createDigestRequest(@RequestBody DigestRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                logger.error("Email is missing or blank.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required and cannot be blank."));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, request);
            UUID technicalId = idFuture.get();

            logger.info("DigestRequest created with ID: {}", technicalId);

            processDigestRequest(technicalId.toString(), request);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /digest/digestRequests/{id} - retrieve DigestRequest by technicalId
    @GetMapping("/digestRequests/{id}")
    public ResponseEntity<?> getDigestRequest(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestRequest with ID {} not found.", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found."));
            }
            DigestRequest request = objectMapper.treeToValue(node, DigestRequest.class);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getDigestRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Exception in getDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /digest/digestData/{id} - retrieve DigestData by technicalId
    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_DIGEST_DATA, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestData with ID {} not found.", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found."));
            }
            DigestData data = objectMapper.treeToValue(node, DigestData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getDigestData: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Exception in getDigestData: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /digest/digestEmail/{id} - retrieve DigestEmail by technicalId
    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<?> getDigestEmail(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_DIGEST_EMAIL, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("DigestEmail with ID {} not found.", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestEmail not found."));
            }
            DigestEmail email = objectMapper.treeToValue(node, DigestEmail.class);
            return ResponseEntity.ok(email);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getDigestEmail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (Exception e) {
            logger.error("Exception in getDigestEmail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // processDigestRequest: validate, call external API, save DigestData, trigger processDigestData
    private void processDigestRequest(String digestRequestId, DigestRequest request) {
        logger.info("Processing DigestRequest with ID: {}", digestRequestId);

        if (!request.getEmail().matches("^[\\w-.]+@[\\w-]+\\.[a-z]{2,}$")) {
            logger.error("Invalid email format for DigestRequest ID: {}", digestRequestId);
            return;
        }

        String endpoint = "/pet/findByStatus";
        String params = "status=available";

        if (request.getRequestPayload() != null && !request.getRequestPayload().isBlank()) {
            try {
                JsonNode jsonNode = objectMapper.readTree(request.getRequestPayload());
                if (jsonNode.has("endpoint")) {
                    endpoint = jsonNode.get("endpoint").asText();
                }
                if (jsonNode.has("params")) {
                    JsonNode paramsNode = jsonNode.get("params");
                    List<String> paramPairs = new ArrayList<>();
                    paramsNode.fieldNames().forEachRemaining(field -> {
                        paramPairs.add(field + "=" + paramsNode.get(field).asText());
                    });
                    params = String.join("&", paramPairs);
                }
            } catch (Exception ex) {
                logger.error("Failed to parse requestPayload for DigestRequest ID {}: {}", digestRequestId, ex.getMessage());
            }
        }

        String url = "https://petstore.swagger.io/v2" + endpoint + "?" + params;

        try {
            String responseBody = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();

            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(digestRequestId);
            digestData.setRetrievedData(responseBody);
            digestData.setFormatType("html");

            CompletableFuture<UUID> digestDataIdFuture = entityService.addItem(ENTITY_DIGEST_DATA, ENTITY_VERSION, digestData);
            UUID digestDataId = digestDataIdFuture.get();

            logger.info("DigestData saved with ID: {} for DigestRequest ID: {}", digestDataId, digestRequestId);

            processDigestData(digestDataId.toString(), digestData);

        } catch (Exception e) {
            logger.error("External API call failed for DigestRequest ID {}: {}", digestRequestId, e.getMessage());
        }
    }

    // processDigestData: compile data into HTML digest, save DigestEmail, trigger processDigestEmail
    private void processDigestData(String digestDataId, DigestData digestData) {
        logger.info("Processing DigestData with ID: {}", digestDataId);

        String compiledHtml = "<html><body><h1>Digest Data</h1><pre>" + digestData.getRetrievedData() + "</pre></body></html>";

        DigestEmail digestEmail = new DigestEmail();
        digestEmail.setDigestRequestId(digestData.getDigestRequestId());
        digestEmail.setEmailContent(compiledHtml);
        digestEmail.setStatus("PENDING");

        try {
            CompletableFuture<UUID> digestEmailIdFuture = entityService.addItem(ENTITY_DIGEST_EMAIL, ENTITY_VERSION, digestEmail);
            UUID digestEmailId = digestEmailIdFuture.get();

            logger.info("DigestEmail saved with ID: {} for DigestRequest ID: {}", digestEmailId, digestData.getDigestRequestId());

            processDigestEmail(digestEmailId.toString(), digestEmail);

        } catch (Exception e) {
            logger.error("Failed to save DigestEmail for DigestData ID {}: {}", digestDataId, e.getMessage());
        }
    }

    // processDigestEmail: send email and update status
    private void processDigestEmail(String digestEmailId, DigestEmail digestEmail) {
        logger.info("Processing DigestEmail with ID: {}", digestEmailId);

        try {
            // Retrieve the DigestRequest entity to get recipient email
            UUID digestRequestUUID = UUID.fromString(digestEmail.getDigestRequestId());
            CompletableFuture<ObjectNode> requestFuture = entityService.getItem(ENTITY_DIGEST_REQUEST, ENTITY_VERSION, digestRequestUUID);
            ObjectNode requestNode = requestFuture.get();
            if (requestNode == null || requestNode.isEmpty()) {
                logger.error("Recipient email not found for DigestEmail ID: {}", digestEmailId);
                digestEmail.setStatus("FAILED");
                // TODO: Update entity - no update method in EntityService - skip
                return;
            }
            DigestRequest digestRequest = objectMapper.treeToValue(requestNode, DigestRequest.class);
            String recipient = digestRequest.getEmail();
            if (recipient == null || recipient.isBlank()) {
                logger.error("Recipient email missing for DigestEmail ID: {}", digestEmailId);
                digestEmail.setStatus("FAILED");
                // TODO: Update entity - no update method in EntityService - skip
                return;
            }

            // Simulate sending email
            logger.info("Sending digest email to: {}", recipient);
            logger.info("Email content: {}", digestEmail.getEmailContent());
            // Simulate success
            digestEmail.setStatus("SENT");
            // TODO: Update entity - no update method in EntityService - skip
            logger.info("DigestEmail ID: {} sent successfully.", digestEmailId);
        } catch (Exception e) {
            logger.error("Failed to send DigestEmail ID: {}: {}", digestEmailId, e.getMessage());
            digestEmail.setStatus("FAILED");
            // TODO: Update entity - no update method in EntityService - skip
        }
    }
}