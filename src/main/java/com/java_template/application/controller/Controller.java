package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.application.entity.HackerNewsItemJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // POST /entity/jobs - create HackerNewsItemJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createHackerNewsItemJob(@RequestBody Map<String, Object> requestBody) throws JsonProcessingException {
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
    public ResponseEntity<?> getHackerNewsItemJob(@PathVariable @NotBlank String technicalId) throws JsonProcessingException {
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
    public ResponseEntity<?> getHackerNewsItem(@PathVariable Long id) throws JsonProcessingException {
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

}