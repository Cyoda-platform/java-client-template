package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/hackernews")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // POST /hackernews/jobs - create a new HackerNewsImportJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createHackerNewsImportJob(@RequestBody JobRequest jobRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
        if (jobRequest == null || jobRequest.items == null || jobRequest.items.isEmpty()) {
            log.error("Job creation failed: items list is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Items list must not be empty"));
        }

        HackerNewsImportJob job = new HackerNewsImportJob();
        job.setImportTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        job.setStatus("PENDING");
        job.setItemCount(jobRequest.items.size());
        job.setDescription(jobRequest.description);

        CompletableFuture<UUID> jobIdFuture = entityService.addItem(HackerNewsImportJob.ENTITY_NAME, ENTITY_VERSION, job);
        UUID jobTechnicalId = jobIdFuture.get();

        log.info("Created HackerNewsImportJob with technicalId={}", jobTechnicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", jobTechnicalId.toString()));
    }

    // GET /hackernews/jobs/{technicalId} - retrieve HackerNewsImportJob by id
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getHackerNewsImportJob(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid;
        try {
            uuid = UUID.fromString(technicalId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(HackerNewsImportJob.ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            log.error("HackerNewsImportJob not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(objectMapper.treeToValue(node, HackerNewsImportJob.class));
    }

    // GET /hackernews/items/{technicalId} - retrieve HackerNewsItem by id
    @GetMapping("/items/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID uuid;
        try {
            uuid = UUID.fromString(technicalId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(HackerNewsItem.ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            log.error("HackerNewsItem not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> response = new HashMap<>();
        ObjectNode itemNode = (ObjectNode) node.get("item");
        if (itemNode != null) {
            response.put("item", objectMapper.treeToValue(itemNode, Map.class));
        } else {
            response.put("item", null);
        }
        if (node.has("state")) {
            response.put("state", node.get("state").asText());
        }
        if (node.has("importTimestamp")) {
            response.put("importTimestamp", node.get("importTimestamp").asText());
        }
        return ResponseEntity.ok(response);
    }

    // Request body classes

    public static class JobRequest {
        public String description;
        public List<Map<String, Object>> items;
    }

    // Entity classes with ENTITY_NAME constants

    @lombok.Data
    public static class HackerNewsImportJob {
        public static final String ENTITY_NAME = "HackerNewsImportJob";
        private String technicalId;
        private String importTimestamp;
        private String status;
        private Integer itemCount;
        private String description;
    }

    @lombok.Data
    public static class HackerNewsItem {
        public static final String ENTITY_NAME = "HackerNewsItem";
        private Long id;
        private String type;
        private String originalJson;
        private String importTimestamp;
        private String state;
    }
}