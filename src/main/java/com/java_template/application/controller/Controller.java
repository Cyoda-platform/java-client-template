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

            // processDigestRequest call removed

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

}