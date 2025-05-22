package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda-users")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "user";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function applied asynchronously before persistence.
    private final Function<JsonNode, CompletableFuture<JsonNode>> processUser = (entityData) -> {
        if (!(entityData instanceof ObjectNode)) {
            // Defensive fallback - return as is
            return CompletableFuture.completedFuture(entityData);
        }
        ObjectNode entity = (ObjectNode) entityData;

        if (!entity.has("userId") || !entity.get("userId").canConvertToInt()) {
            entity.put("error", "Missing or invalid userId");
            return CompletableFuture.completedFuture(entity);
        }

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
                                // Clear previous fields except technical metadata (if any)
                                // We keep userId for reference
                                String originalUserId = entity.get("userId").asText();
                                entity.removeAll();
                                entity.put("userId", originalUserId);
                                // Merge fetched data
                                entity.setAll((ObjectNode) dataNode);
                                entity.put("fetchedFromApi", true);
                                entity.put("fetchStatus", "success");
                                return entity;
                            } else {
                                entity.put("error", "User data missing in API response");
                                entity.put("fetchStatus", "failed");
                                return entity;
                            }
                        } else if (statusCode == 404) {
                            entity.put("error", "User not found in external API");
                            entity.put("fetchStatus", "not_found");
                            return entity;
                        } else {
                            entity.put("error", "Unexpected HTTP response code: " + statusCode);
                            entity.put("fetchStatus", "error");
                            return entity;
                        }
                    } catch (Exception ex) {
                        logger.error("Error parsing API response or updating entity", ex);
                        entity.put("error", "Exception while processing user data: " + ex.getMessage());
                        entity.put("fetchStatus", "exception");
                        return entity;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Exception during HTTP request to fetch user data", ex);
                    entity.put("error", "Exception during HTTP request: " + ex.getMessage());
                    entity.put("fetchStatus", "exception");
                    return entity;
                });
    };

    // Endpoint to add/fetch user entity. Workflow enriches entity asynchronously.
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchUser(@RequestBody @Valid UserIdRequest request) {
        int userId = request.getUserId();
        logger.info("Received request to fetch user with ID {}", userId);

        ObjectNode initialEntity = objectMapper.createObjectNode();
        initialEntity.put("userId", userId);

        try {
            UUID technicalId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, initialEntity, processUser).get();
            logger.info("Added user entity with technicalId={}", technicalId);
            return ResponseEntity.ok(Map.of(
                    "message", "User entity created and will be enriched asynchronously",
                    "technicalId", technicalId));
        } catch (Exception e) {
            logger.error("Failed to add user entity", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to add user entity: " + e.getMessage()));
        }
    }

    // Endpoint to get persisted user entity by technicalId
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(@PathVariable UUID technicalId) {
        logger.info("Received request to get user with technicalId {}", technicalId);
        try {
            ObjectNode userData = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).get();
            if (userData == null || userData.isEmpty()) {
                logger.warn("User data not found for technicalId={}", technicalId);
                return ResponseEntity.status(404).body(Map.of("error", "User data not found. Please fetch first."));
            }
            return ResponseEntity.ok(userData);
        } catch (Exception e) {
            logger.error("Failed to get user entity", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get user entity: " + e.getMessage()));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIdRequest {
        @Min(value = 1, message = "userId must be greater than or equal to 1")
        private int userId;
    }
}