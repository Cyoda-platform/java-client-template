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

            // processDigestRequest removed

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