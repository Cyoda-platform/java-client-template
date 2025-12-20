package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.service.ItemSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for searching Hacker News items via the Cyoda entity search API.
 * Provides a POST /items/search endpoint which accepts a JSON body with search parameters.
 */
@RestController
@RequestMapping("/items")
@CrossOrigin(origins = "*")
public class ItemsSearchController {

    private static final Logger logger = LoggerFactory.getLogger(ItemsSearchController.class);

    private final ItemSearchService itemSearchService;

    public ItemsSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /**
     * Search for Hacker News items using the Cyoda entity search API.
     * POST /items/search
     * Request body: { "q": "search text", "type": "story", "limit": 20, "offset": 0 }
     *
     * @param request the search request body
     * @return ResponseEntity with search results or error response
     */
    @PostMapping("/search")
    public ResponseEntity<JsonNode> search(@RequestBody SearchRequest request) {
        String q = request == null ? null : request.getQ();
        String type = request == null ? null : request.getType();
        int limit = request == null ? 20 : request.getLimit();
        int offset = request == null ? 0 : request.getOffset();

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
