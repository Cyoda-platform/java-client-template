package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.HackerNewsItemJob;
import com.java_template.application.entity.HackerNewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, HackerNewsItemJob> hackerNewsItemJobCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<Long, HackerNewsItem> hackerNewsItemCache = new ConcurrentHashMap<>();
    private final AtomicLong hackerNewsItemIdCounter = new AtomicLong(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /prototype/jobs - create HackerNewsItemJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createHackerNewsItemJob(@RequestBody Map<String, Object> requestBody) {
        try {
            if (!requestBody.containsKey("hnItemJson")) {
                log.error("Missing hnItemJson in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing hnItemJson in request body");
            }
            String hnItemJson = requestBody.get("hnItemJson").toString();
            if (hnItemJson.isBlank()) {
                log.error("Empty hnItemJson");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("hnItemJson cannot be blank");
            }

            String technicalId = String.valueOf(hackerNewsItemJobIdCounter.getAndIncrement());
            HackerNewsItemJob job = new HackerNewsItemJob();
            job.setTechnicalId(technicalId);
            job.setHnItemJson(hnItemJson);
            job.setStatus("PENDING");
            job.setCreatedAt(System.currentTimeMillis());

            hackerNewsItemJobCache.put(technicalId, job);

            processHackerNewsItemJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            log.info("Created HackerNewsItemJob with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Exception in createHackerNewsItemJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /prototype/jobs/{technicalId} - retrieve HackerNewsItemJob
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getHackerNewsItemJob(@PathVariable String technicalId) {
        HackerNewsItemJob job = hackerNewsItemJobCache.get(technicalId);
        if (job == null) {
            log.error("HackerNewsItemJob not found for technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/items/{id} - retrieve HackerNewsItem by HN item id
    @GetMapping("/items/{id}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable Long id) {
        HackerNewsItem item = hackerNewsItemCache.get(id);
        if (item == null) {
            log.error("HackerNewsItem not found for id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Item not found");
        }
        return ResponseEntity.ok(item);
    }

    // Business logic for processing HackerNewsItemJob
    private void processHackerNewsItemJob(String technicalId, HackerNewsItemJob job) {
        log.info("Processing HackerNewsItemJob technicalId={}", technicalId);
        job.setStatus("PROCESSING");
        try {
            // Validate JSON format
            Map<?,?> hnItemMap = objectMapper.readValue(job.getHnItemJson(), Map.class);

            // Validate essential fields in hnItemMap
            if (!hnItemMap.containsKey("id") || !(hnItemMap.get("id") instanceof Number)) {
                log.error("Invalid or missing 'id' field in hnItemJson");
                job.setStatus("FAILED");
                hackerNewsItemJobCache.put(technicalId, job);
                return;
            }

            Long itemId = ((Number) hnItemMap.get("id")).longValue();

            // Construct HackerNewsItem entity
            HackerNewsItem item = new HackerNewsItem();
            item.setId(itemId);
            item.setRawJson(job.getHnItemJson());

            // Extract fields with safe casting and defaults
            Object byObj = hnItemMap.get("by");
            item.setBy(byObj != null ? byObj.toString() : "");

            Object descendantsObj = hnItemMap.get("descendants");
            item.setDescendants(descendantsObj instanceof Number ? ((Number)descendantsObj).intValue() : 0);

            Object kidsObj = hnItemMap.get("kids");
            if (kidsObj instanceof List<?>) {
                List<Long> kidsList = new ArrayList<>();
                for (Object k : (List<?>) kidsObj) {
                    if (k instanceof Number) {
                        kidsList.add(((Number) k).longValue());
                    }
                }
                item.setKids(kidsList);
            } else {
                item.setKids(Collections.emptyList());
            }

            Object scoreObj = hnItemMap.get("score");
            item.setScore(scoreObj instanceof Number ? ((Number)scoreObj).intValue() : 0);

            Object timeObj = hnItemMap.get("time");
            item.setTime(timeObj instanceof Number ? ((Number)timeObj).longValue() : 0L);

            Object titleObj = hnItemMap.get("title");
            item.setTitle(titleObj != null ? titleObj.toString() : "");

            Object typeObj = hnItemMap.get("type");
            item.setType(typeObj != null ? typeObj.toString() : "");

            Object urlObj = hnItemMap.get("url");
            item.setUrl(urlObj != null ? urlObj.toString() : "");

            // Validate item entity
            if (!item.isValid()) {
                log.error("HackerNewsItem validation failed for item id {}", itemId);
                job.setStatus("FAILED");
                hackerNewsItemJobCache.put(technicalId, job);
                return;
            }

            // Save HackerNewsItem immutably
            hackerNewsItemCache.put(itemId, item);

            // Update job status and completedAt timestamp
            job.setStatus("COMPLETED");
            job.setCompletedAt(System.currentTimeMillis());
            hackerNewsItemJobCache.put(technicalId, job);

            log.info("Successfully processed HackerNewsItemJob technicalId={} and stored item id={}", technicalId, itemId);
        } catch (JsonProcessingException e) {
            log.error("JSON parsing error in HackerNewsItemJob technicalId={}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            hackerNewsItemJobCache.put(technicalId, job);
        } catch (Exception e) {
            log.error("Unexpected error processing HackerNewsItemJob technicalId={}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            hackerNewsItemJobCache.put(technicalId, job);
        }
    }
}