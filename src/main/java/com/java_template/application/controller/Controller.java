package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/hackernews")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /hackernews/jobs - create a new HackerNewsImportJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createHackerNewsImportJob(@RequestBody JobRequest jobRequest) {
        try {
            if (jobRequest == null || jobRequest.items == null || jobRequest.items.isEmpty()) {
                log.error("Job creation failed: items list is empty or null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Items list must not be empty"));
            }

            HackerNewsImportJob job = new HackerNewsImportJob();
            job.setImportTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            job.setStatus("PENDING");
            job.setItemCount(jobRequest.items.size());
            job.setDescription(jobRequest.description);

            // Add job entity to EntityService
            CompletableFuture<UUID> jobIdFuture = entityService.addItem(HackerNewsImportJob.ENTITY_NAME, ENTITY_VERSION, job);
            UUID jobTechnicalId = jobIdFuture.get();

            log.info("Created HackerNewsImportJob with technicalId={}", jobTechnicalId);

            processHackerNewsImportJob(jobTechnicalId, job, jobRequest.items);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", jobTechnicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument during job creation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during job creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /hackernews/jobs/{technicalId} - retrieve HackerNewsImportJob by id
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getHackerNewsImportJob(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(HackerNewsImportJob.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("HackerNewsImportJob not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Remove technicalId field from response if needed or leave as is
            return ResponseEntity.ok(objectMapper.treeToValue(node, HackerNewsImportJob.class));
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                log.error("Illegal argument when retrieving job {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getCause().getMessage()));
            }
            log.error("Error retrieving job {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            log.error("Unexpected error retrieving job {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /hackernews/items/{technicalId} - retrieve HackerNewsItem by id
    @GetMapping("/items/{technicalId}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
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
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                log.error("Illegal argument when retrieving item {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getCause().getMessage()));
            }
            log.error("Error retrieving item {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            log.error("Unexpected error retrieving item {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    private void processHackerNewsImportJob(UUID jobTechnicalId, HackerNewsImportJob job, List<Map<String, Object>> items) {
        boolean allSuccess = true;
        List<HackerNewsItem> itemEntities = new ArrayList<>();
        for (Map<String, Object> itemMap : items) {
            try {
                Object idObj = itemMap.get("id");
                String itemIdStr = idObj != null ? idObj.toString() : null;
                if (itemIdStr == null || itemIdStr.isBlank()) {
                    log.error("Skipping item with missing id");
                    allSuccess = false;
                    continue;
                }

                HackerNewsItem item = new HackerNewsItem();
                item.setId(Long.parseLong(itemIdStr));
                String typeStr = itemMap.get("type") != null ? itemMap.get("type").toString() : "";
                item.setType(typeStr);
                item.setImportTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

                // Store originalJson string in the item entity
                item.setOriginalJson(objectMapper.writeValueAsString(itemMap));

                processHackerNewsItem(item);

                itemEntities.add(item);

            } catch (Exception e) {
                log.error("Failed processing item in job {}", jobTechnicalId, e);
                allSuccess = false;
            }
        }
        try {
            if (!itemEntities.isEmpty()) {
                CompletableFuture<List<UUID>> addItemsFuture = entityService.addItems(HackerNewsItem.ENTITY_NAME, ENTITY_VERSION, itemEntities);
                addItemsFuture.get();
            }
            job.setStatus(allSuccess ? "COMPLETED" : "FAILED");
            CompletableFuture<UUID> updateJobFuture = entityService.addItem(HackerNewsImportJob.ENTITY_NAME, ENTITY_VERSION, job);
            updateJobFuture.get();
            log.info("Job {} processing completed with status {}", jobTechnicalId, job.getStatus());
        } catch (Exception e) {
            log.error("Failed updating job {} status", jobTechnicalId, e);
        }
    }

    private void processHackerNewsItem(HackerNewsItem item) {
        boolean hasId = item.getId() != null;
        boolean hasType = item.getType() != null && !item.getType().isBlank();
        if (hasId && hasType) {
            item.setState("VALID");
            log.info("Item marked VALID");
        } else {
            item.setState("INVALID");
            log.info("Item marked INVALID");
        }
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
        private int itemCount;
        private String description;
    }

    @lombok.Data
    public static class HackerNewsItem {
        public static final String ENTITY_NAME = "HackerNewsItem";
        private String technicalId;
        private Long id;
        private String type;
        private String originalJson;
        private String importTimestamp;
        private String state;
    }
}