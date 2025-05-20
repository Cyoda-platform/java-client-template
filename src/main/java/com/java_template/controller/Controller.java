package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";
    private static final String ENTITY_NAME_SUBSCRIBER = "subscriber";
    private static final String ENTITY_NAME_CATFACT = "catFact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Endpoint to subscribe a user by email
    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Retrieve all subscribers and check if email exists to avoid duplicates
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenCompose(arrayNode -> {
            for (JsonNode node : arrayNode) {
                String nodeEmail = node.path("email").asText(null);
                if (nodeEmail != null && nodeEmail.equals(email)) {
                    logger.info("Subscribe attempt for existing email: {}", email);
                    return CompletableFuture.completedFuture((ObjectNode) node);
                }
            }
            // Not found - create new subscriber ObjectNode
            ObjectNode newSubscriber = objectMapper.createObjectNode();
            newSubscriber.put("email", email);
            // Add new subscriber without workflow
            return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, newSubscriber)
                    .thenApply(technicalId -> {
                        newSubscriber.put("technicalId", technicalId.toString());
                        logger.info("New subscriber added: {}", email);
                        return newSubscriber;
                    });
        });
    }

    // Endpoint to unsubscribe a user by email
    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, String>> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Retrieve all subscribers to find matching email and technicalId
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenCompose(arrayNode -> {
            UUID technicalIdToDelete = null;
            for (JsonNode node : arrayNode) {
                String nodeEmail = node.path("email").asText(null);
                if (nodeEmail != null && nodeEmail.equals(email)) {
                    String technicalIdStr = node.path("technicalId").asText(null);
                    if (technicalIdStr != null) {
                        try {
                            technicalIdToDelete = UUID.fromString(technicalIdStr);
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid technicalId format for subscriber {}: {}", email, technicalIdStr);
                            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Corrupted subscriber technicalId");
                        }
                    }
                    break;
                }
            }
            if (technicalIdToDelete == null) {
                logger.warn("Unsubscribe attempt for non-existing email: {}", email);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
            }
            // Delete the subscriber
            return entityService.deleteItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, technicalIdToDelete)
                    .thenApply(deletedId -> {
                        logger.info("Unsubscribed: {}", email);
                        return Map.of("message", "Unsubscribed successfully");
                    });
        });
    }

    // Endpoint to fetch weekly cat fact and send to all subscribers
    @PostMapping(value = "/catfact/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> sendWeeklyCatFact() {
        logger.info("Starting weekly cat fact fetch and send");
        return CompletableFuture.supplyAsync(() -> {
            try {
                var response = restTemplate.getForEntity(new URI(CAT_FACT_API_URL), String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    logger.error("Failed to fetch cat fact: status {}", response.getStatusCodeValue());
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
                }
                JsonNode root = objectMapper.readTree(response.getBody());
                String factText = root.path("fact").asText(null);
                if (factText == null || factText.isBlank()) {
                    logger.error("Cat fact missing or empty in API response");
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact missing");
                }
                ObjectNode catFactNode = objectMapper.createObjectNode();
                catFactNode.put("fact", factText);
                return catFactNode;
            } catch (Exception e) {
                logger.error("Exception during cat fact fetch", e);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
            }
        }).thenCompose(catFactNode ->
                entityService.addItem(ENTITY_NAME_CATFACT, ENTITY_VERSION, catFactNode)
                        .thenApply(technicalId -> {
                            catFactNode.put("technicalId", technicalId.toString());
                            logger.info("Cat fact persisted with technicalId {}", technicalId);
                            return catFactNode;
                        })
        );
    }

    // Endpoint to get reporting summary with total subscribers, opens, unsubscribes (opens and unsubscribes zero because no external tracking)
    @GetMapping(value = "/reporting/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ReportingSummary> getReportingSummary() {
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(subscribersArray -> {
                    int totalSubscribers = subscribersArray.size();
                    int totalEmailOpens = 0; // no external storage, so zero
                    int totalUnsubscribes = 0; // no external storage, so zero
                    logger.info("Reporting summary: subscribers={}, opens={}, unsubscribes={}", totalSubscribers, totalEmailOpens, totalUnsubscribes);
                    return new ReportingSummary(totalSubscribers, totalEmailOpens, totalUnsubscribes);
                });
    }

    // Global exception handler for ResponseStatusException to return JSON error
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

    // Data classes and request DTOs

    @Data
    public static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class UnsubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ReportingSummary {
        private int totalSubscribers;
        private int totalEmailOpens;
        private int totalUnsubscribes;
    }
}