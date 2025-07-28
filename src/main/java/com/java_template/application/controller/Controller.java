package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.application.entity.HackerNewsItemJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@AllArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /entity/jobs - create HackerNewsItemJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createHackerNewsItemJob(@RequestBody Map<String, Object> requestBody) {
        try {
            if (!requestBody.containsKey("hnItemJson")) {
                logger.error("Missing hnItemJson in request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing hnItemJson in request body");
            }
            String hnItemJson = requestBody.get("hnItemJson").toString();
            if (hnItemJson.isBlank()) {
                logger.error("Empty hnItemJson");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("hnItemJson cannot be blank");
            }

            HackerNewsItemJob job = new HackerNewsItemJob();
            job.setHnItemJson(hnItemJson);
            job.setStatus("PENDING");
            job.setCreatedAt(System.currentTimeMillis());

            // Add job entity to external service
            CompletableFuture<UUID> idFuture = entityService.addItem("HackerNewsItemJob", ENTITY_VERSION, job);
            UUID technicalId = idFuture.get(); // blocking here, could be improved for async

            job.setTechnicalId(technicalId.toString());

            processHackerNewsItemJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            logger.info("Created HackerNewsItemJob with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createHackerNewsItemJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception in createHackerNewsItemJob: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        } catch (Exception e) {
            logger.error("Exception in createHackerNewsItemJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /entity/jobs/{technicalId} - retrieve HackerNewsItemJob
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getHackerNewsItemJob(@PathVariable String technicalId) {
        try {
            UUID techId = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("HackerNewsItemJob", ENTITY_VERSION, techId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("HackerNewsItemJob not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            HackerNewsItemJob job = objectMapper.treeToValue(node, HackerNewsItemJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId format");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception in getHackerNewsItemJob: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        } catch (Exception e) {
            logger.error("Exception in getHackerNewsItemJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /entity/items/{id} - retrieve HackerNewsItem by HN item id (field 'id' in entity)
    @GetMapping("/items/{id}")
    public ResponseEntity<?> getHackerNewsItem(@PathVariable Long id) {
        try {
            // Build condition to find HackerNewsItem by id field equal to id
            Condition condition = Condition.of("$.id", "EQUALS", id);
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    "HackerNewsItem", ENTITY_VERSION, conditionRequest, true);

            ArrayNode results = filteredItemsFuture.get();
            if (results == null || results.isEmpty()) {
                logger.error("HackerNewsItem not found for id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Item not found");
            }
            // Assuming only one item matches id
            ObjectNode node = (ObjectNode) results.get(0);
            HackerNewsItem item = objectMapper.treeToValue(node, HackerNewsItem.class);
            return ResponseEntity.ok(item);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception in getHackerNewsItem: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        } catch (Exception e) {
            logger.error("Exception in getHackerNewsItem: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // Business logic for processing HackerNewsItemJob
    private void processHackerNewsItemJob(UUID technicalId, HackerNewsItemJob job) {
        logger.info("Processing HackerNewsItemJob technicalId={}", technicalId);
        job.setStatus("PROCESSING");
        try {
            // Validate JSON format
            Map<?, ?> hnItemMap = objectMapper.readValue(job.getHnItemJson(), Map.class);

            // Validate essential fields in hnItemMap
            if (!hnItemMap.containsKey("id") || !(hnItemMap.get("id") instanceof Number)) {
                logger.error("Invalid or missing 'id' field in hnItemJson");
                job.setStatus("FAILED");
                updateJobStatus(technicalId, job);
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
            item.setDescendants(descendantsObj instanceof Number ? ((Number) descendantsObj).intValue() : 0);

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
            item.setScore(scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0);

            Object timeObj = hnItemMap.get("time");
            item.setTime(timeObj instanceof Number ? ((Number) timeObj).longValue() : 0L);

            Object titleObj = hnItemMap.get("title");
            item.setTitle(titleObj != null ? titleObj.toString() : "");

            Object typeObj = hnItemMap.get("type");
            item.setType(typeObj != null ? typeObj.toString() : "");

            Object urlObj = hnItemMap.get("url");
            item.setUrl(urlObj != null ? urlObj.toString() : "");

            // Validate item entity
            if (!item.isValid()) {
                logger.error("HackerNewsItem validation failed for item id {}", itemId);
                job.setStatus("FAILED");
                updateJobStatus(technicalId, job);
                return;
            }

            // Save HackerNewsItem immutably via EntityService
            CompletableFuture<UUID> itemIdFuture = entityService.addItem("HackerNewsItem", ENTITY_VERSION, item);
            itemIdFuture.get(); // blocking here

            // Update job status and completedAt timestamp
            job.setStatus("COMPLETED");
            job.setCompletedAt(System.currentTimeMillis());
            updateJobStatus(technicalId, job);

            logger.info("Successfully processed HackerNewsItemJob technicalId={} and stored item id={}", technicalId, itemId);
        } catch (Exception e) {
            logger.error("Error processing HackerNewsItemJob technicalId={}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            try {
                updateJobStatus(technicalId, job);
            } catch (Exception ex) {
                logger.error("Failed to update job status to FAILED for technicalId={}: {}", technicalId, ex.getMessage());
            }
        }
    }

    private void updateJobStatus(UUID technicalId, HackerNewsItemJob job) throws Exception {
        // TODO: EntityService has no update method, so skipping actual update
        // Could be implemented with delete + add or other mechanisms if available
        // For now, just log the status update
        logger.info("Job status update (not persisted): technicalId={}, status={}", technicalId, job.getStatus());
    }
}