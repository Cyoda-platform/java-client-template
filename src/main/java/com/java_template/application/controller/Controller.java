package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.application.entity.ExternalApiData;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // POST /entity/digestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<Map<String, String>> createDigestRequestJob(@Valid @RequestBody DigestRequestJob job) throws ExecutionException, InterruptedException {
        if (job == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request body is required"));
        }

        // Set initial status and createdAt if missing
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            job.setStatus("PENDING");
        }
        if (job.getCreatedAt() == null) {
            job.setCreatedAt(java.time.Instant.now());
        }

        if (!job.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid DigestRequestJob fields"));
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "DigestRequestJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.get();
        String technicalIdStr = technicalId.toString();

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
    }

    // GET /entity/digestRequestJob/{id}
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<DigestRequestJob> getDigestRequestJob(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "DigestRequestJob",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
        return ResponseEntity.ok(job);
    }

    // GET /entity/externalApiData/{id}
    @GetMapping("/externalApiData/{id}")
    public ResponseEntity<ExternalApiData> getExternalApiData(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "ExternalApiData",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        ExternalApiData data = objectMapper.treeToValue(node, ExternalApiData.class);
        return ResponseEntity.ok(data);
    }

    // GET /entity/digestEmail/{id}
    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<DigestEmail> getDigestEmail(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "DigestEmail",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        DigestEmail email = objectMapper.treeToValue(node, DigestEmail.class);
        return ResponseEntity.ok(email);
    }
}