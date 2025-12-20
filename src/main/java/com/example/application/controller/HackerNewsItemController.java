package com.example.application.controller;

import com.example.application.entity.hacker_news_item.version_1.HackerNewsItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * HackerNewsItemController - REST API for Hacker News items
 * 
 * Provides endpoints to:
 * - POST /hn/items: Submit a Hacker News item for storage
 * - GET /hn/items/{id}: Retrieve a stored item by its ID
 */
@RestController
@RequestMapping("/hn/items")
@CrossOrigin(origins = "*")
public class HackerNewsItemController {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HackerNewsItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /hn/items
     * Submit a Hacker News item for storage
     * 
     * Request body: Raw JSON matching Firebase Hacker News API format
     * Response: 201 Created with stored item, or 400 Bad Request if validation fails
     */
    @PostMapping
    public ResponseEntity<?> submitItem(@RequestBody JsonNode itemJson) {
        try {
            // Extract id and type from the JSON
            JsonNode idNode = itemJson.get("id");
            JsonNode typeNode = itemJson.get("type");

            // Validate required fields
            if (idNode == null || idNode.isNull() || typeNode == null || typeNode.isNull()) {
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Missing required fields: 'id' and 'type' must be present"
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Create HackerNewsItem entity
            HackerNewsItem item = new HackerNewsItem();
            item.setId(idNode.asLong());
            item.setType(typeNode.asText());
            item.setRawJson(itemJson);

            // Create entity via EntityService (triggers workflow)
            EntityWithMetadata<HackerNewsItem> response = entityService.create(item);
            logger.info("HackerNewsItem created with ID: {}", response.metadata().getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to submit item", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to submit item: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * GET /hn/items/{id}
     * Retrieve a Hacker News item by its Hacker News ID
     * 
     * Path parameter: id - The Hacker News item ID
     * Response: 200 OK with original JSON payload, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getItem(@PathVariable Long id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(HackerNewsItem.ENTITY_NAME)
                    .withVersion(HackerNewsItem.ENTITY_VERSION);

            // Search for item by business ID (Hacker News ID)
            EntityWithMetadata<HackerNewsItem> result = entityService.findByBusinessId(
                    modelSpec, String.valueOf(id), "id", HackerNewsItem.class);

            if (result == null) {
                return ResponseEntity.notFound().build();
            }

            logger.info("Retrieved HackerNewsItem with ID: {}", id);
            return ResponseEntity.ok(result.entity().getRawJson());
        } catch (Exception e) {
            logger.error("Failed to retrieve item with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve item: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

