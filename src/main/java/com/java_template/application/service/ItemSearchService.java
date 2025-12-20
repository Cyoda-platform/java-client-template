package com.java_template.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import reactor.core.publisher.Mono;

/**
 * ABOUTME: Service for searching Hacker News items using the Cyoda entity search API.
 * Handles building search requests, forwarding authorization tokens, and error handling.
 */
@Service
public class ItemSearchService {

    private static final Logger logger = LoggerFactory.getLogger(ItemSearchService.class);
    private static final String SEARCH_ENDPOINT = "/v1/entities/search";

    private final WebClient.Builder webClientBuilder;
    private final Config config;
    private final ObjectMapper objectMapper;

    public ItemSearchService(WebClient.Builder webClientBuilder, Config config, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Searches for Hacker News items using the Cyoda entity search API.
     *
     * @param q the search query (required)
     * @param type the entity type to search (optional)
     * @param limit the maximum number of results (default 20)
     * @param offset the pagination offset (default 0)
     * @return Mono containing the search response as JsonNode
     */
    public Mono<JsonNode> search(String q, String type, int limit, int offset) {
        logger.debug("Searching for items with query: {}, type: {}, limit: {}, offset: {}", q, type, limit, offset);

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("query", q);
        if (type != null && !type.isEmpty()) {
            requestBody.put("entityType", type);
        }
        requestBody.put("limit", limit);
        requestBody.put("offset", offset);

        // Build WebClient request
        WebClient webClient = webClientBuilder.baseUrl(config.getCyodaApiUrl()).build();
        var request = webClient.post()
                .uri(SEARCH_ENDPOINT)
                .bodyValue(requestBody);

        // Add authorization header if token is available
        String token = System.getenv("CYODA_API_TOKEN");
        if (token != null && !token.isEmpty()) {
            request = request.header("Authorization", "Bearer " + token);
            logger.debug("Authorization token added to request");
        }

        return request
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnError(error -> logger.error("Error searching items: {}", error.getMessage(), error))
                .onErrorMap(WebClientException.class, e -> {
                    logger.error("WebClient error during search: {}", e.getMessage());
                    return e;
                });
    }
}

