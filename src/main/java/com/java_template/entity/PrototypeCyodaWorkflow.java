package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String ENTITY_NAME = "subscriber";
    private static final int ENTITY_VERSION = ENTITY_VERSION; // imported constant

    private static final String WEEKLY_CAT_FACT_ENTITY = "weeklyCatFact";
    private static final int WEEKLY_CAT_FACT_VERSION = 1;

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Invalid email format")
        private String email;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberResponse {
        private UUID subscriberId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendWeeklyResponse {
        private String status;
        private int sentCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberCountResponse {
        private int totalSubscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionSummary {
        private int emailOpens;
        private int linkClicks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionReportResponse {
        private InteractionSummary interactions;
    }

    /**
     * Workflow function to process Subscriber entity before persistence.
     * Validates no duplicate email exists.
     * Normalizes email, sets subscribedAt timestamp.
     * Throws ResponseStatusException on duplicate.
     */
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        String emailRaw = entity.path("email").asText(null);
        if (emailRaw == null || emailRaw.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Email is required");
        }
        String email = emailRaw.toLowerCase(Locale.ROOT);
        entity.put("email", email);

        // Check duplicates asynchronously
        Condition cond = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest searchCond = SearchConditionRequest.group("AND", cond);

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCond)
                .thenApply(existingSubscribers -> {
                    if (!existingSubscribers.isEmpty()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Email already subscribed");
                    }
                    // Set subscribedAt if missing or invalid
                    if (!entity.hasNonNull("subscribedAt")) {
                        entity.put("subscribedAt", Instant.now().toString());
                    } else {
                        try {
                            Instant.parse(entity.get("subscribedAt").asText());
                        } catch (Exception e) {
                            entity.put("subscribedAt", Instant.now().toString());
                        }
                    }
                    // Additional validation or state changes can be done here
                    return entity;
                });
    }

    /**
     * Workflow function to process WeeklyCatFact entity before persistence.
     * Fetches cat fact, sends emails to all subscribers asynchronously.
     * Does not modify entity data except adding sentAt timestamp.
     */
    private CompletableFuture<ObjectNode> processWeeklyCatFact(ObjectNode entity) {
        // Fetch cat fact asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
                if (jsonStr == null || jsonStr.isEmpty()) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty response from CatFact API");
                }
                return objectMapper.readTree(jsonStr);
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact from external API", e);
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
            }
        }).thenCompose(catFactJson -> {
            String fact = catFactJson.path("fact").asText(null);
            if (fact == null || fact.isBlank()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty cat fact received");
            }
            // Get all subscribers
            return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                    .thenCompose(subscribers -> {
                        List<CompletableFuture<Void>> emailFutures = new ArrayList<>();
                        for (JsonNode subscriber : subscribers) {
                            String name = subscriber.path("name").asText("");
                            String email = subscriber.path("email").asText("");
                            if (email != null && !email.isBlank()) {
                                emailFutures.add(sendEmailAsync(name, email, fact));
                            }
                        }
                        return CompletableFuture.allOf(emailFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> {
                                    // Mark when emails sent for audit if needed
                                    entity.put("sentAt", Instant.now().toString());
                                    return entity;
                                });
                    });
        });
    }

    /**
     * Async mock email sender.
     * Replace with real email sending logic.
     */
    private CompletableFuture<Void> sendEmailAsync(String name, String email, String fact) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Sending cat fact email to {} <{}>: {}", name, email, fact);
            try {
                Thread.sleep(50); // Simulate delay for sending email
            } catch (InterruptedException ignored) {
            }
        });
    }

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscriberResponse>> subscribe(@Valid @RequestBody SubscriberRequest request) {
        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("email", request.getEmail());
        if (request.getName() != null) {
            subscriberNode.put("name", request.getName());
        }
        // Use workflow function to validate and set fields before persistence
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriberNode, this::processSubscriber)
                .thenApply(id -> {
                    logger.info("New subscriber registered: {}", request.getEmail());
                    return ResponseEntity.ok(new SubscriberResponse(id, "Subscription successful"));
                });
    }

    /**
     * Endpoint to trigger sending weekly cat fact emails.
     * Sends emails asynchronously via workflow function.
     */
    @PostMapping("/facts/send-weekly")
    public CompletableFuture<ResponseEntity<SendWeeklyResponse>> sendWeeklyCatFact() {
        ObjectNode triggerEntity = objectMapper.createObjectNode();
        triggerEntity.put("triggeredAt", Instant.now().toString());
        // Add item of special entity to trigger workflow sending emails
        return entityService.addItem(WEEKLY_CAT_FACT_ENTITY, WEEKLY_CAT_FACT_VERSION, triggerEntity, this::processWeeklyCatFact)
                .thenApply(id -> {
                    logger.info("Triggered weekly cat fact send-out");
                    // Cannot reliably count sent emails here, so use -1
                    return ResponseEntity.ok(new SendWeeklyResponse("success", -1));
                })
                .exceptionally(ex -> {
                    logger.error("Failed to send weekly cat fact", ex);
                    return ResponseEntity.ok(new SendWeeklyResponse("failed", 0));
                });
    }

    @GetMapping("/report/subscribers/count")
    public CompletableFuture<ResponseEntity<SubscriberCountResponse>> getSubscriberCount() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(results -> {
                    int count = results.size();
                    logger.info("Subscriber count requested: {}", count);
                    return ResponseEntity.ok(new SubscriberCountResponse(count));
                });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionSummary {
        private int emailOpens;
        private int linkClicks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionReportResponse {
        private InteractionSummary interactions;
    }

    @GetMapping("/report/interactions")
    public ResponseEntity<InteractionReportResponse> getInteractionReport() {
        // No interaction data source provided, returning zeros
        logger.info("Interaction report requested: opens=0, clicks=0");
        InteractionSummary summary = new InteractionSummary(0, 0);
        return ResponseEntity.ok(new InteractionReportResponse(summary));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}