package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.service.ItemSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: REST controller for searching Hacker News items via the Cyoda entity search API.
 * Provides a GET /items/search endpoint with query parameter validation and error handling.
 */
@RestController
@RequestMapping("/items")
@CrossOrigin(origins = "*")
public class ItemsSearchController {

    private static final Logger logger = LoggerFactory.getLogger(ItemsSearchController.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;

    private final ItemSearchService itemSearchService;

    public ItemsSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /**
     * Search for Hacker News items using the Cyoda entity search API.
     * GET /items/search?q=<query>&type=<type>&limit=<limit>&offset=<offset>
     *
     * @param q the search query (required)
     * @param type the entity type to search (optional)
     * @param limit the maximum number of results (default 20)
     * @param offset the pagination offset (default 0)
     * @return ResponseEntity with search results or error response
     */
    @GetMapping("/search")
    public ResponseEntity<JsonNode> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(value = "offset", defaultValue = "" + DEFAULT_OFFSET) int offset) {

        logger.info("Search request received: q={}, type={}, limit={}, offset={}", q, type, limit, offset);

        // Validate required parameter
        if (q == null || q.trim().isEmpty()) {
            logger.warn("Search request missing required parameter 'q'");
            return ResponseEntity.badRequest().build();
        }

        try {
            // Call service and block to get response (MVC pattern)
            JsonNode result = itemSearchService.search(q, type, limit, offset).block();
            logger.info("Search completed successfully for query: {}", q);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

