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
}