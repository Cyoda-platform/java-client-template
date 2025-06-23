```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "Subscriber";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Workflow function for Subscriber entity
    private CompletableFuture<Subscriber> processSubscriber(Subscriber subscriber) {
        // Example: could modify subscriber here before saving, e.g. trim email, name, or set defaults
        if (subscriber.getEmail() != null) {
            subscriber.setEmail(subscriber.getEmail().trim().toLowerCase());
        }
        if (subscriber.getName() != null) {
            subscriber.setName(subscriber.getName().trim());
        }
        // Set subscribedAt if null
        if (subscriber.getSubscribedAt() == null) {
            subscriber.setSubscribedAt(Instant.now());
        }
        return CompletableFuture.completedFuture(subscriber);
    }

    // Workflow function for CatFact entity
    private CompletableFuture<CatFact> processCatFact(CatFact catFact) {
        // e.g. ensure createdAt is set
        if (catFact.getCreatedAt() == null) {
            catFact.setCreatedAt(Instant.now());
        }
        return CompletableFuture.completedFuture(catFact);
    }

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> subscribeUser(@RequestBody @Valid SubscriptionRequest request) {
        logger.info("Received subscription request for email={}", request.getEmail());
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Email is required");
        }

        // Check existing subscriber by email via getItemsByCondition
        String condition = String.format("{\"email\":\"%s\"}", request.getEmail());
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenCompose(itemsArray -> {
                    for (JsonNode node : itemsArray) {
                        String email = node.path("email").asText(null);
                        if (email != null && email.equalsIgnoreCase(request.getEmail())) {
                            UUID existingId = UUID.fromString(node.path("technicalId").asText());
                            logger.info("Email {} already subscribed", request.getEmail());
                            SubscriptionResponse resp = new SubscriptionResponse(existingId, "Already subscribed");
                            return CompletableFuture.completedFuture(ResponseEntity.ok(resp));
                        }
                    }
                    Subscriber sub = new Subscriber(null, request.getEmail(), request.getName(), Instant.now());
                    // Pass workflow function processSubscriber to addItem
                    return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, sub, this::processSubscriber)
                            .thenApply(id -> {
                                sub.setId(id);
                                logger.info("User subscribed with id={}", id);
                                InteractionMetrics metrics = new InteractionMetrics(0,0,0);
                                // Save metrics as separate entity or manage later if needed; skipping here as no replacement API provided
                                SubscriptionResponse resp = new SubscriptionResponse(id, "Subscription successful");
                                return ResponseEntity.ok(resp);
                            });
                });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<List<Subscriber>> getSubscribers() {
        logger.info("Retrieving all subscribers");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Subscriber> list = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        UUID id = UUID.fromString(node.path("technicalId").asText());
                        String email = node.path("email").asText(null);
                        String name = node.path("name").asText(null);
                        Instant subscribedAt = null;
                        JsonNode subscribedNode = node.path("subscribedAt");
                        if (!subscribedNode.isMissingNode() && subscribedNode.isTextual()) {
                            try {
                                subscribedAt = Instant.parse(subscribedNode.asText());
                            } catch (Exception e) {
                                logger.warn("Failed to parse subscribedAt for id {}: {}", id, e.getMessage());
                            }
                        }
                        Subscriber sub = new Subscriber(id, email, name, subscribedAt);
                        list.add(sub);
                    }
                    logger.info("Retrieved {} subscribers", list.size());
                    return list;
                });
    }

    @PostMapping("/facts/send-weekly")
    public CompletableFuture<ResponseEntity<SendFactResponse>> sendWeeklyFact() {
        logger.info("Triggered weekly cat fact retrieval and email send-out");
        CompletableFuture<JsonNode> factFuture = CompletableFuture.supplyAsync(() -> {
            try {
                String response = restTemplate.getForObject(new URI("https://catfact.ninja/fact"), String.class);
                return objectMapper.readTree(response);
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact from external API: {}", e.getMessage(), e);
                throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
            }
        });

        return factFuture.thenCompose(factJson -> {
            String factText = factJson.path("fact").asText(null);
            if (factText == null) {
                logger.error("Cat fact not found in API response");
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Invalid cat fact response");
            }
            CatFact fact = new CatFact(null, factText, Instant.now());
            // Pass workflow function processCatFact to addItem
            return entityService.addItem("CatFact", ENTITY_VERSION, fact, this::processCatFact).thenCompose(factId -> {
                fact.setFactId(factId);
                return getSubscribers().thenCompose(subscribersList -> {
                    sendEmailsToSubscribersAsync(fact, subscribersList);
                    int subscriberCount = subscribersList.size();
                    logger.info("Weekly cat fact '{}' prepared for sending to {} subscribers", factText, subscriberCount);
                    SendFactResponse response = new SendFactResponse(factId, factText, subscriberCount);
                    return CompletableFuture.completedFuture(ResponseEntity.ok(response));
                });
            });
        });
    }

    @Async
    public CompletableFuture<Void> sendEmailsToSubscribersAsync(CatFact fact, List<Subscriber> subscribersList) {
        subscribersList.forEach(sub -> {
            logger.info("Sending email to {}: {}", sub.getEmail(), fact.getFact());
            // No replacement API for InteractionMetrics, so simulate as before
            // This is just logging and in-memory increment simulation no longer applicable here
        });
        return CompletableFuture.completedFuture(null);
    }

    @GetMapping("/reporting/metrics")
    public CompletableFuture<ReportingMetrics> getReportingMetrics() {
        // Since InteractionMetrics are in-memory previously, no replacement API available
        // We'll calculate only totalSubscribers via EntityService
        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    int totalSubscribers = arrayNode.size();
                    // Cannot calculate emailsSent, opens, clicks as no replacement API for metrics
                    int totalEmailsSent = 0;
                    int totalOpens = 0;
                    int totalClicks = 0;
                    double averageOpenRate = 0.0;
                    double averageClickRate = 0.0;
                    logger.info("Reporting metrics computed: subscribers={}, emailsSent={}, openRate={}, clickRate={}",
                            totalSubscribers, totalEmailsSent, averageOpenRate, averageClickRate);
                    return new ReportingMetrics(totalSubscribers, totalEmailsSent, averageOpenRate, averageClickRate);
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;

        @Size(max = 100)
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriptionResponse {
        private UUID subscriberId;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private UUID id;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class CatFact {
        private UUID factId;
        private String fact;
        private Instant createdAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InteractionMetrics {
        private int emailsSent;
        private int emailOpens;
        private int linkClicks;

        public void incrementEmailsSent() {
            this.emailsSent++;
        }
        public void incrementEmailOpens() {
            this.emailOpens++;
        }
        public void incrementLinkClicks() {
            this.linkClicks++;
        }
    }

    @Data
    @AllArgsConstructor
    public static class SendFactResponse {
        private UUID factId;
        private String factText;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    public static class ReportingMetrics {
        private int totalSubscribers;
        private int emailsSent;
        private double averageOpenRate;
        private double averageClickRate;
    }
}
```
---

### Explanation of changes:
- Added workflow functions `processSubscriber` and `processCatFact` with the required signature: they take the entity object as argument and return a `CompletableFuture` of the same entity.
- Updated all `entityService.addItem` calls to pass the corresponding workflow function as the last argument.
- The workflow functions can modify the entity before persistence (e.g., trimming strings, setting timestamps).
- The rest of the code remains unchanged except for passing the workflow parameter.

This satisfies the requirement of adding the workflow function parameter and implementing it for the entities used in this controller.