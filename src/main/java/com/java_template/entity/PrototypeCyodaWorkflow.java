```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "subscriber";

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized.");
    }

    /**
     * Workflow function that processes a subscriber entity before persistence.
     * You can modify the subscriber state here or perform other entity operations
     * with different entity models.
     *
     * @param entity the subscriber entity data
     * @return the processed entity
     */
    private Subscriber processSubscriber(Subscriber entity) {
        // Example: you can add logic here to modify subscriber before saving
        // For now, just return as is
        return entity;
    }

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<Subscriber>> createSubscriber(@RequestBody @Valid SubscriberRequest request) {
        logger.info("Received subscriber creation request for email: {}", request.getEmail());

        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);

        return allSubscribersFuture.thenCompose(allSubscribers -> {
            boolean alreadyExists = false;
            for (JsonNode node : allSubscribers) {
                String email = node.path("email").asText();
                if (email.equalsIgnoreCase(request.getEmail())) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Subscriber with this email already exists");
            }

            Subscriber subscriber = new Subscriber(null, request.getEmail(), "subscribed");

            // Pass the workflow function processSubscriber as parameter to addItem
            return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriber, this::processSubscriber)
                    .thenApply(technicalId -> {
                        subscriber.setSubscriberId(technicalId.toString());
                        logger.info("Subscriber created with technicalId: {}", technicalId);
                        return ResponseEntity.created(URI.create("/api/cyodaentity/subscribers/" + technicalId)).body(subscriber);
                    });
        });
    }

    @GetMapping("/subscribers/{subscriberId}")
    public CompletableFuture<ResponseEntity<Subscriber>> getSubscriber(@PathVariable String subscriberId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(subscriberId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid subscriber ID format");
        }
        CompletableFuture<ObjectNode> subscriberFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);

        return subscriberFuture.thenApply(node -> {
            if (node == null || node.isEmpty()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscriber not found");
            }
            Subscriber subscriber = new Subscriber();
            subscriber.setSubscriberId(node.path("technicalId").asText());
            subscriber.setEmail(node.path("email").asText());
            subscriber.setStatus(node.path("status").asText());
            logger.info("Returning subscriber info for technicalId: {}", subscriberId);
            return ResponseEntity.ok(subscriber);
        });
    }

    @PostMapping("/facts/sendWeekly")
    public ResponseEntity<FactSendResponse> sendWeeklyCatFact() {
        logger.info("Triggering weekly cat fact ingestion and email sending.");

        JsonNode catFactJson;
        try {
            String responseString = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            catFactJson = objectMapper.readTree(responseString);
        } catch (Exception e) {
            logger.error("Failed to retrieve cat fact from external API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to retrieve cat fact");
        }

        String catFactText = catFactJson.path("fact").asText(null);
        if (catFactText == null) {
            logger.error("Cat fact missing in API response");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Invalid cat fact response");
        }

        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);

        int subscriberCount = 0;
        try {
            // block here to get subscriber count synchronously
            ArrayNode subscribersNode = allSubscribersFuture.get();
            subscriberCount = subscribersNode.size();
        } catch (Exception e) {
            logger.error("Failed to get subscribers for count", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get subscribers");
        }

        // TODO: Replace with real async email sending logic
        CompletableFuture.runAsync(() -> sendCatFactEmails(catFactText));

        FactSendResponse response = new FactSendResponse(subscriberCount, catFactText);
        logger.info("Cat fact sent to {} subscribers", subscriberCount);
        return ResponseEntity.ok(response);
    }

    @Async
    void sendCatFactEmails(String catFactText) {
        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        allSubscribersFuture.thenAccept(subscribersNode -> {
            logger.info("Sending cat fact emails to {} subscribers...", subscribersNode.size());
            for (JsonNode node : subscribersNode) {
                String email = node.path("email").asText();
                logger.info("Sending email to {}: {}", email, catFactText);
            }
        }).exceptionally(ex -> {
            logger.error("Failed to send cat fact emails", ex);
            return null;
        });
    }

    @GetMapping("/report/subscribers")
    public CompletableFuture<ResponseEntity<SubscriberReport>> getSubscriberReport() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION).thenApply(subscribersNode -> {
            int total = subscribersNode.size();
            logger.info("Subscriber report requested, total: {}", total);
            return ResponseEntity.ok(new SubscriberReport(total));
        });
    }

    @GetMapping("/report/interaction")
    public ResponseEntity<InteractionReport> getInteractionReport() {
        // Interaction reports remain local cache as original code, since no external service usage specified
        int emailOpens = 0; // no data from external service
        int linkClicks = 0;
        InteractionReport report = new InteractionReport(emailOpens, linkClicks);
        logger.info("Interaction report requested: opens={}, clicks={}", emailOpens, linkClicks);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getStatusCode().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error occurred"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String subscriberId;
        private String email;
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class FactSendResponse {
        private int sentToSubscribers;
        private String catFact;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriberReport {
        private int totalSubscribers;
    }

    @Data
    @AllArgsConstructor
    public static class InteractionReport {
        private int emailOpens;
        private int linkClicks;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String message;
    }
}
```
---

### Explanation:

- Added a private workflow function `processSubscriber` with the required naming convention `process{entity_name}` (`Subscriber` entity → `processSubscriber` method).
- This method accepts a `Subscriber` entity, can modify it, and returns it back.
- Updated the call to `entityService.addItem` in `createSubscriber` to pass `this::processSubscriber` as the workflow function argument.
- No other logic was changed, preserving all original functionality.