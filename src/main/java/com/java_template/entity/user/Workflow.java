package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        return processValidateUserId(entity)
                .thenCompose(validEntity -> {
                    if (validEntity.has("error")) {
                        return CompletableFuture.completedFuture(validEntity);
                    }
                    return processFetchUserData(validEntity);
                })
                .thenApply(this::processPostFetch);
    }

    private CompletableFuture<ObjectNode> processValidateUserId(ObjectNode entity) {
        if (!entity.has("userId") || !entity.get("userId").canConvertToInt()) {
            entity.put("error", "Missing or invalid userId");
            return CompletableFuture.completedFuture(entity);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processFetchUserData(ObjectNode entity) {
        int userId = entity.get("userId").intValue();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String reqresUrl = "https://reqres.in/api/users/" + userId;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(reqresUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> {
                    try {
                        int statusCode = response.statusCode();
                        if (statusCode == 200) {
                            JsonNode rootNode = objectMapper.readTree(response.body());
                            JsonNode dataNode = rootNode.get("data");
                            if (dataNode != null && !dataNode.isNull() && dataNode.isObject()) {
                                // Clear previous fields except userId
                                String originalUserId = entity.get("userId").asText();
                                entity.removeAll();
                                entity.put("userId", originalUserId);
                                entity.setAll((ObjectNode) dataNode);
                                entity.put("fetchedFromApi", true);
                                entity.put("fetchStatus", "success");
                            } else {
                                entity.put("error", "User data missing in API response");
                                entity.put("fetchStatus", "failed");
                            }
                        } else if (statusCode == 404) {
                            entity.put("error", "User not found in external API");
                            entity.put("fetchStatus", "not_found");
                        } else {
                            entity.put("error", "Unexpected HTTP response code: " + statusCode);
                            entity.put("fetchStatus", "error");
                        }
                    } catch (Exception ex) {
                        logger.error("Error parsing API response or updating entity", ex);
                        entity.put("error", "Exception while processing user data: " + ex.getMessage());
                        entity.put("fetchStatus", "exception");
                    }
                    return entity;
                })
                .exceptionally(ex -> {
                    logger.error("Exception during HTTP request to fetch user data", ex);
                    entity.put("error", "Exception during HTTP request: " + ex.getMessage());
                    entity.put("fetchStatus", "exception");
                    return entity;
                });
    }

    private ObjectNode processPostFetch(ObjectNode entity) {
        // Placeholder for any business logic after fetching user data
        // Currently no additional processing required
        return entity;
    }
}