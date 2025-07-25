package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.EmailDispatch;
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
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);
    private final AtomicLong digestDataIdCounter = new AtomicLong(1);
    private final AtomicLong emailDispatchIdCounter = new AtomicLong(1);

    // POST /entity/digestRequest - Create DigestRequest
    @PostMapping("/digestRequest")
    public ResponseEntity<?> createDigestRequest(@RequestBody DigestRequest request) {
        try {
            if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
                logger.error("DigestRequest creation failed: email is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
            }
            request.setCreatedAt(Instant.now());
            request.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem("DigestRequest", ENTITY_VERSION, request);
            UUID technicalId = idFuture.get();

            logger.info("DigestRequest created with technicalId: {}", technicalId);

            processDigestRequest(technicalId.toString(), request);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/digestRequest/{id} - Retrieve DigestRequest
    @GetMapping("/digestRequest/{id}")
    public ResponseEntity<?> getDigestRequest(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DigestRequest not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found"));
            }
            // Convert ObjectNode to DigestRequest
            DigestRequest request = JsonUtils.convertObjectNodeToEntity(node, DigestRequest.class);
            return ResponseEntity.ok(request);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID or bad request in getDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("DigestRequest not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestRequest not found"));
            }
            logger.error("Error retrieving DigestRequest: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getDigestRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/digestData/{id} - Retrieve DigestData
    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestData", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DigestData not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found"));
            }
            DigestData data = JsonUtils.convertObjectNodeToEntity(node, DigestData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID or bad request in getDigestData: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("DigestData not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DigestData not found"));
            }
            logger.error("Error retrieving DigestData: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getDigestData: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/emailDispatch/{id} - Retrieve EmailDispatch
    @GetMapping("/emailDispatch/{id}")
    public ResponseEntity<?> getEmailDispatch(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("EmailDispatch", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("EmailDispatch not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailDispatch not found"));
            }
            EmailDispatch email = JsonUtils.convertObjectNodeToEntity(node, EmailDispatch.class);
            return ResponseEntity.ok(email);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID or bad request in getEmailDispatch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("EmailDispatch not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailDispatch not found"));
            }
            logger.error("Error retrieving EmailDispatch: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Exception in getEmailDispatch: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Business logic: processDigestRequest
    private void processDigestRequest(String technicalId, DigestRequest entity) {
        logger.info("Processing DigestRequest with technicalId: {}", technicalId);
        try {
            if (entity.getEmail() == null || entity.getEmail().isBlank()) {
                logger.error("DigestRequest {} validation failed: email is blank", technicalId);
                entity.setStatus("FAILED");
                updateEntity("DigestRequest", technicalId, entity);
                return;
            }
            entity.setStatus("PROCESSING");
            updateEntity("DigestRequest", technicalId, entity);

            DigestData digestData = new DigestData();
            digestData.setDigestRequestId(technicalId);
            digestData.setCreatedAt(Instant.now());
            digestData.setStatus("PENDING");

            CompletableFuture<UUID> digestDataIdFuture = entityService.addItem("DigestData", ENTITY_VERSION, digestData);
            UUID digestDataTechnicalId = digestDataIdFuture.get();

            processDigestData(digestDataTechnicalId.toString(), digestData);

        } catch (Exception e) {
            logger.error("Exception in processDigestRequest for technicalId {}: {}", technicalId, e.getMessage(), e);
            entity.setStatus("FAILED");
            updateEntity("DigestRequest", technicalId, entity);
        }
    }

    // Business logic: processDigestData
    private void processDigestData(String technicalId, DigestData entity) {
        logger.info("Processing DigestData with technicalId: {}", technicalId);
        try {
            entity.setStatus("PROCESSING");
            updateEntity("DigestData", technicalId, entity);

            RestTemplate restTemplate = new RestTemplate();
            String apiUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

            String apiResponse = restTemplate.getForObject(apiUrl, String.class);
            if (apiResponse == null || apiResponse.isBlank()) {
                logger.error("Empty response from Petstore API for DigestData technicalId {}", technicalId);
                entity.setStatus("FAILED");
                updateEntity("DigestData", technicalId, entity);
                return;
            }

            entity.setApiData(apiResponse);
            entity.setStatus("SUCCESS");
            updateEntity("DigestData", technicalId, entity);

            EmailDispatch emailDispatch = new EmailDispatch();
            emailDispatch.setDigestRequestId(entity.getDigestRequestId());
            emailDispatch.setEmailContent("");
            emailDispatch.setStatus("PENDING");

            CompletableFuture<UUID> emailDispatchIdFuture = entityService.addItem("EmailDispatch", ENTITY_VERSION, emailDispatch);
            UUID emailDispatchTechnicalId = emailDispatchIdFuture.get();

            processEmailDispatch(emailDispatchTechnicalId.toString(), emailDispatch);

        } catch (Exception e) {
            logger.error("Exception in processDigestData for technicalId {}: {}", technicalId, e.getMessage(), e);
            entity.setStatus("FAILED");
            updateEntity("DigestData", technicalId, entity);
        }
    }

    // Business logic: processEmailDispatch
    private void processEmailDispatch(String technicalId, EmailDispatch entity) {
        logger.info("Processing EmailDispatch with technicalId: {}", technicalId);
        try {
            entity.setStatus("PROCESSING");
            updateEntity("EmailDispatch", technicalId, entity);

            // Retrieve associated DigestRequest
            String digestRequestId = entity.getDigestRequestId();
            UUID digestRequestTechnicalId = UUID.fromString(digestRequestId);
            CompletableFuture<ObjectNode> digestRequestFuture = entityService.getItem("DigestRequest", ENTITY_VERSION, digestRequestTechnicalId);
            ObjectNode digestRequestNode = digestRequestFuture.get();
            if (digestRequestNode == null) {
                logger.error("Associated DigestRequest not found for EmailDispatch technicalId: {}", technicalId);
                entity.setStatus("FAILED");
                updateEntity("EmailDispatch", technicalId, entity);
                return;
            }
            DigestRequest digestRequest = JsonUtils.convertObjectNodeToEntity(digestRequestNode, DigestRequest.class);

            // Retrieve associated successful DigestData by condition: digestRequestId = digestRequestId AND status = "SUCCESS"
            Condition cond1 = Condition.of("$.digestRequestId", "EQUALS", digestRequestId);
            Condition cond2 = Condition.of("$.status", "EQUALS", "SUCCESS");
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond1, cond2);

            CompletableFuture<ArrayNode> digestDataArrayFuture = entityService.getItemsByCondition("DigestData", ENTITY_VERSION, conditionRequest, true);
            ArrayNode digestDataArray = digestDataArrayFuture.get();

            if (digestDataArray == null || digestDataArray.size() == 0) {
                logger.error("Associated successful DigestData not found for EmailDispatch technicalId: {}", technicalId);
                entity.setStatus("FAILED");
                updateEntity("EmailDispatch", technicalId, entity);
                return;
            }

            ObjectNode digestDataNode = (ObjectNode) digestDataArray.get(0);
            DigestData digestData = JsonUtils.convertObjectNodeToEntity(digestDataNode, DigestData.class);

            String emailContent = "Digest for request ID: " + entity.getDigestRequestId() + "\n\nData:\n" + digestData.getApiData();
            entity.setEmailContent(emailContent);

            logger.info("Sending email to {} with digest content length {}", digestRequest.getEmail(), emailContent.length());
            // Simulate sending email by logging

            entity.setStatus("SENT");
            entity.setSentAt(Instant.now());
            updateEntity("EmailDispatch", technicalId, entity);

            // Update DigestRequest status to COMPLETED
            digestRequest.setStatus("COMPLETED");
            updateEntity("DigestRequest", digestRequestId, digestRequest);

        } catch (Exception e) {
            logger.error("Exception in processEmailDispatch for technicalId {}: {}", technicalId, e.getMessage(), e);
            entity.setStatus("FAILED");
            updateEntity("EmailDispatch", technicalId, entity);
        }
    }

    // Helper method to update entities - TODO replace with actual update when supported
    private void updateEntity(String entityModel, String technicalIdStr, Object entity) {
        // EntityService does not support update, so leave as is or add TODO
        // TODO: Implement update operation when supported
        logger.debug("Update requested for {} with technicalId {}, but operation not supported yet", entityModel, technicalIdStr);
    }

    // Utility class for JSON conversion between ObjectNode and entities
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convertObjectNodeToEntity(ObjectNode node, Class<T> clazz) {
            try {
                // Remove technicalId field before conversion if present
                if (node.has("technicalId")) {
                    node.remove("technicalId");
                }
                return mapper.treeToValue(node, clazz);
            } catch (Exception e) {
                logger.error("Error converting ObjectNode to {}: {}", clazz.getSimpleName(), e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }
}