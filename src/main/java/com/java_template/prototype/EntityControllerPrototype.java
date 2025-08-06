package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HackerNewsImportJob> hackerNewsImportJobCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsImportJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, HackerNewsItem> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /prototype/jobs - create a new HackerNewsImportJob
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, String>> createHackerNewsImportJob(@RequestBody JobRequest jobRequest) {
        if (jobRequest == null || jobRequest.items == null || jobRequest.items.isEmpty()) {
            log.error("Job creation failed: items list is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Items list must not be empty"));
        }

        String technicalId = "job-" + hackerNewsImportJobIdCounter.getAndIncrement();
        String importTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        HackerNewsImportJob job = new HackerNewsImportJob();
        job.setTechnicalId(technicalId);
        job.setImportTimestamp(importTimestamp);
        job.setStatus("PENDING");
        job.setItemCount(jobRequest.items.size());
        job.setDescription(jobRequest.description);

        hackerNewsImportJobCache.put(technicalId, job);

        log.info("Created HackerNewsImportJob with technicalId={}", technicalId);

        processHackerNewsImportJob(technicalId, job, jobRequest.items);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/jobs/{technicalId} - retrieve HackerNewsImportJob by id
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<HackerNewsImportJob> getHackerNewsImportJob(@PathVariable String technicalId) {
        HackerNewsImportJob job = hackerNewsImportJobCache.get(technicalId);
        if (job == null) {
            log.error("HackerNewsImportJob not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/items/{technicalId} - retrieve HackerNewsItem by id
    @GetMapping("/items/{technicalId}")
    public ResponseEntity<Map<String, Object>> getHackerNewsItem(@PathVariable String technicalId) {
        HackerNewsItem item = hackerNewsItemCache.get(technicalId);
        if (item == null) {
            log.error("HackerNewsItem not found: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String,Object> originalJsonMap = objectMapper.readValue(item.getOriginalJson(), Map.class);
            response.put("item", originalJsonMap);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse originalJson for item {}", technicalId, e);
            response.put("item", null);
        }
        response.put("state", item.getState());
        response.put("importTimestamp", item.getImportTimestamp());
        return ResponseEntity.ok(response);
    }

    private void processHackerNewsImportJob(String technicalId, HackerNewsImportJob job, List<Map<String, Object>> items) {
        boolean allSuccess = true;
        for (Map<String, Object> itemMap : items) {
            try {
                String itemIdStr = itemMap.get("id") != null ? itemMap.get("id").toString() : null;
                if (itemIdStr == null || itemIdStr.isBlank()) {
                    log.error("Skipping item with missing id");
                    allSuccess = false;
                    continue;
                }
                // Generate a technicalId for item
                String itemTechnicalId = "item-" + hackerNewsItemIdCounter.getAndIncrement();
                String importTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

                // Serialize original JSON preserving structure
                String originalJson = objectMapper.writeValueAsString(itemMap);

                HackerNewsItem item = new HackerNewsItem();
                item.setTechnicalId(itemTechnicalId);
                item.setId(Long.parseLong(itemIdStr));
                String typeStr = itemMap.get("type") != null ? itemMap.get("type").toString() : "";
                item.setType(typeStr);
                item.setOriginalJson(originalJson);
                item.setImportTimestamp(importTimestamp);

                hackerNewsItemCache.put(itemTechnicalId, item);

                processHackerNewsItem(itemTechnicalId, item);

            } catch (Exception e) {
                log.error("Failed processing item in job {}", technicalId, e);
                allSuccess = false;
            }
        }
        job.setStatus(allSuccess ? "COMPLETED" : "FAILED");
        hackerNewsImportJobCache.put(technicalId, job);
        log.info("Job {} processing completed with status {}", technicalId, job.getStatus());
    }

    private void processHackerNewsItem(String technicalId, HackerNewsItem item) {
        boolean hasId = item.getId() != null;
        boolean hasType = item.getType() != null && !item.getType().isBlank();
        if (hasId && hasType) {
            item.setState("VALID");
            log.info("Item {} marked VALID", technicalId);
        } else {
            item.setState("INVALID");
            log.info("Item {} marked INVALID", technicalId);
        }
        hackerNewsItemCache.put(technicalId, item);
    }

    // Request body classes

    public static class JobRequest {
        public String description;
        public List<Map<String, Object>> items;
    }
}