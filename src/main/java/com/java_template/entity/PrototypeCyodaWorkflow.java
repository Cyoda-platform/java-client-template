```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
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

    // Workflow function for subscriber entity: processsubscriber
    private Function<Object, Object> processsubscriber = entity -> {
        // You can modify the entity state here asynchronously before persistence
        // For example, set subscribedAt to now if null, etc.
        if (entity instanceof Subscriber) {
            Subscriber subscriber = (Subscriber) entity;
            if (subscriber.getSubscribedAt() == null) {
                subscriber.setSubscribedAt(Instant.now());
            }
        }
        return entity;
    };

    // Workflow function for catFact entity: processcatFact
    private Function<Object, Object> processcatFact = entity -> {
        if (entity instanceof CatFact) {
            CatFact catFact = (CatFact) entity;
            if (catFact.getTimestamp() == null) {
                catFact.setTimestamp(Instant.now());
            }
        }
        return entity;
    };

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Subscriber> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Retrieve all subscribers and check if email exists
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenCompose(arrayNode -> {
            for (JsonNode node : arrayNode) {
                String nodeEmail = node.path("email").asText(null);
                if (nodeEmail != null && nodeEmail.equals(email)) {
                    UUID technicalId = UUID.fromString(node.path("technicalId").asText());
                    Subscriber existingSubscriber = Subscriber.builder()
                            .email(nodeEmail)
                            .subscribedAt(Instant.parse(node.path("subscribedAt").asText()))
                            .technicalId(technicalId)
                            .build();
                    logger.info("Subscribe attempt for existing email: {}", email);
                    return CompletableFuture.completedFuture(existingSubscriber);
                }
            }
            // Not found - add new subscriber with workflow function
            Subscriber subscriber = new Subscriber(email, Instant.now());
            return entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, processsubscriber)
                    .thenApply(technicalId -> {
                        subscriber.setTechnicalId(technicalId);
                        logger.info("New subscriber added: {}", email);
                        return subscriber;
                    });
        });
    }

    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, String>> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Retrieve all subscribers to find matching email and technicalId
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).thenCompose(arrayNode -> {
            UUID technicalIdToDelete = null;
            for (JsonNode node : arrayNode) {
                String nodeEmail = node.path("email").asText(null);
                if (nodeEmail != null && nodeEmail.equals(email)) {
                    technicalIdToDelete = UUID.fromString(node.path("technicalId").asText());
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

    @PostMapping(value = "/catfact/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<CatFactResponse> sendWeeklyCatFact() {
        logger.info("Starting weekly cat fact fetch and send");
        JsonNode root;
        try {
            var response = restTemplate.getForEntity(new URI(CAT_FACT_API_URL), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.error("Failed to fetch cat fact: status {}", response.getStatusCodeValue());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
            }
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            logger.error("Exception during cat fact fetch", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
        }
        String factText = root.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            logger.error("Cat fact missing in API response");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact missing");
        }
        CatFact catFact = new CatFact(null, factText, Instant.now());

        // Add catFact entity with workflow function
        return entityService.addItem(ENTITY_NAME_CATFACT, ENTITY_VERSION, catFact, processcatFact)
                .thenCompose(technicalId -> {
                    catFact.setTechnicalId(technicalId);
                    // Retrieve all subscribers to send emails asynchronously
                    return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                            .thenApply(subscribersArray -> {
                                sendEmailsToSubscribers(catFact, subscribersArray);
                                return new CatFactResponse(
                                        technicalId.toString(),
                                        factText,
                                        catFact.getTimestamp(),
                                        subscribersArray.size());
                            });
                });
    }

    @Async
    void sendEmailsToSubscribers(CatFact catFact, ArrayNode subscribersArray) {
        for (JsonNode node : subscribersArray) {
            String email = node.path("email").asText(null);
            if (email != null) {
                logger.info("Sending cat fact email to {}", email); // TODO: replace with real email logic
            }
        }
    }

    @GetMapping(value = "/reporting/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ReportingSummary> getReportingSummary() {
        // Since email opens and unsubscribes are not stored externally, keep them zero
        return entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(subscribersArray -> {
                    int totalSubscribers = subscribersArray.size();
                    int totalEmailOpens = 0; // no external storage, so zero
                    int totalUnsubscribes = 0; // no external storage, so zero
                    logger.info("Reporting summary: subscribers={}, opens={}, unsubscribes={}", totalSubscribers, totalEmailOpens, totalUnsubscribes);
                    return new ReportingSummary(totalSubscribers, totalEmailOpens, totalUnsubscribes);
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
        @lombok.Setter(onMethod_ = {@com.fasterxml.jackson.annotation.JsonIgnore})
        private UUID technicalId;

        public static SubscriberBuilder builder() {
            return new SubscriberBuilder();
        }

        public static class SubscriberBuilder {
            private String email;
            private Instant subscribedAt;
            private UUID technicalId;

            public SubscriberBuilder email(String email) {
                this.email = email;
                return this;
            }

            public SubscriberBuilder subscribedAt(Instant subscribedAt) {
                this.subscribedAt = subscribedAt;
                return this;
            }

            public SubscriberBuilder technicalId(UUID technicalId) {
                this.technicalId = technicalId;
                return this;
            }

            public Subscriber build() {
                return new Subscriber(email, subscribedAt, technicalId);
            }
        }
    }

    @Data
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    static class UnsubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CatFact {
        private String id; // kept for compatibility but not used
        private String fact;
        private Instant timestamp;
        @lombok.Setter(onMethod_ = {@com.fasterxml.jackson.annotation.JsonIgnore})
        private UUID technicalId;
    }

    @Data
    @AllArgsConstructor
    static class CatFactResponse {
        private String catFactId;
        private String fact;
        private Instant sentAt;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    static class ReportingSummary {
        private int totalSubscribers;
        private int totalEmailOpens;
        private int totalUnsubscribes;
    }
}
```
**Explanation of changes:**

- Added two workflow functions `processsubscriber` and `processcatFact`, each matching the naming convention `process{entity_name}`.
- These functions are `Function<Object, Object>` and modify the entity as needed before persistence.
- Updated `entityService.addItem()` calls to include the workflow function as the last argument.
- For subscriber addition: `entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, processsubscriber)`
- For catFact addition: `entityService.addItem(ENTITY_NAME_CATFACT, ENTITY_VERSION, catFact, processcatFact)`

This respects the new method signature and usage pattern described.