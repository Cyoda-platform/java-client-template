package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function for Subscriber entity
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processSubscriber = entity -> {
        logger.debug("processSubscriber workflow started for email: {}", entity.get("email").asText());

        String email = normalizeEmail(entity.get("email").asText(null));
        if (email == null) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalArgumentException("Invalid email"));
            return failedFuture;
        }
        entity.put("email", email);
        entity.put("subscribedAt", Instant.now().toString());

        // Remove UnsubscribedSubscriber record if exists
        return entityService.getItem("UnsubscribedSubscriber", ENTITY_VERSION, email)
                .thenCompose(optUnsubscribed -> {
                    if (optUnsubscribed.isPresent()) {
                        return entityService.deleteItem("UnsubscribedSubscriber", ENTITY_VERSION, email)
                                .exceptionally(ex -> {
                                    logger.warn("Failed to delete UnsubscribedSubscriber for {}: {}", email, ex.getMessage());
                                    return null;
                                })
                                .thenApply(v -> entity);
                    }
                    return CompletableFuture.completedFuture(entity);
                });
    };

    // Workflow function for CatFact entity
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processCatFact = entity -> {
        logger.info("processCatFact workflow started");

        try {
            String response = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (response == null) {
                CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new IllegalStateException("No response from Cat Fact API"));
                return failedFuture;
            }
            JsonNode root = objectMapper.readTree(response);
            String fact = root.path("fact").asText(null);
            if (!StringUtils.hasText(fact)) {
                CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new IllegalStateException("Cat fact missing in API response"));
                return failedFuture;
            }
            entity.put("fact", fact);
            entity.put("retrievedAt", Instant.now().toString());

            // Optionally assign ID based on current week for easy retrieval
            String currentWeek = getCurrentIsoWeek();
            entity.put("id", currentWeek);

            return CompletableFuture.completedFuture(entity);

        } catch (IOException e) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    };

    // Workflow function for EmailInteraction entity
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processEmailInteraction = entity -> {
        logger.info("processEmailInteraction workflow started");

        String eventType = entity.get("eventType").asText(null);
        String email = normalizeEmail(entity.get("email").asText(null));
        if (!("open".equals(eventType) || "click".equals(eventType)) || email == null) {
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Invalid interaction event or email"));
            return failed;
        }

        String currentWeek = getCurrentIsoWeek();

        return entityService.getItem("InteractionSummary", ENTITY_VERSION, currentWeek)
                .thenCompose(optSummary -> {
                    ObjectNode summaryNode;
                    if (optSummary.isPresent()) {
                        summaryNode = (ObjectNode) optSummary.get();
                    } else {
                        summaryNode = objectMapper.createObjectNode();
                        summaryNode.put("emailOpens", 0);
                        summaryNode.put("emailClicks", 0);
                    }
                    if ("open".equals(eventType)) {
                        summaryNode.put("emailOpens", summaryNode.path("emailOpens").asInt(0) + 1);
                    } else if ("click".equals(eventType)) {
                        summaryNode.put("emailClicks", summaryNode.path("emailClicks").asInt(0) + 1);
                    }

                    // Add or update summary asynchronously, with no workflow to avoid recursion
                    return entityService.addItem("InteractionSummary", ENTITY_VERSION, summaryNode, e -> CompletableFuture.completedFuture(e))
                            .handle((savedId, ex) -> {
                                if (ex != null) {
                                    logger.error("Failed to update InteractionSummary for week {}: {}", currentWeek, ex.getMessage());
                                } else {
                                    logger.debug("Updated InteractionSummary for week {}", currentWeek);
                                }
                                return entity;
                            });
                });
    };

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<String>> subscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);

        return entityService.getItem("Subscriber", ENTITY_VERSION, email)
                .thenCompose(opt -> {
                    if (opt.isPresent()) {
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.CONFLICT).body("Email already subscribed"));
                    }
                    ObjectNode subscriberNode = objectMapper.createObjectNode();
                    subscriberNode.put("email", email);

                    return entityService.addItem("Subscriber", ENTITY_VERSION, subscriberNode, processSubscriber)
                            .thenApply(id -> ResponseEntity.status(HttpStatus.CREATED).body("Subscribed successfully"));
                });
    }

    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<String>> unsubscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);

        return entityService.getItem("Subscriber", ENTITY_VERSION, email)
                .thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Email not found"));
                    }
                    return entityService.deleteItem("Subscriber", ENTITY_VERSION, email)
                            .thenCompose(v -> {
                                ObjectNode unsubscribed = objectMapper.createObjectNode();
                                unsubscribed.put("email", email);
                                unsubscribed.put("unsubscribedAt", Instant.now().toString());

                                return entityService.addItem("UnsubscribedSubscriber", ENTITY_VERSION, unsubscribed, e -> CompletableFuture.completedFuture(e));
                            })
                            .thenApply(v -> ResponseEntity.ok("Unsubscribed successfully"));
                });
    }

    @PostMapping(value = "/subscribers/resubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<String>> resubscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);

        return entityService.getItem("Subscriber", ENTITY_VERSION, email)
                .thenCompose(optSubscriber -> {
                    if (optSubscriber.isPresent()) {
                        return CompletableFuture.completedFuture(ResponseEntity.ok("Already subscribed"));
                    }
                    return entityService.getItem("UnsubscribedSubscriber", ENTITY_VERSION, email)
                            .thenCompose(optUnsubscribed -> {
                                if (optUnsubscribed.isEmpty()) {
                                    return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("Email not found or never subscribed"));
                                }
                                return entityService.deleteItem("UnsubscribedSubscriber", ENTITY_VERSION, email)
                                        .thenCompose(v -> {
                                            ObjectNode subscriberNode = objectMapper.createObjectNode();
                                            subscriberNode.put("email", email);
                                            return entityService.addItem("Subscriber", ENTITY_VERSION, subscriberNode, processSubscriber)
                                                    .thenApply(id -> ResponseEntity.ok("Resubscribed successfully"));
                                        });
                            });
                });
    }

    @PostMapping("/facts/ingest-and-send")
    public CompletableFuture<ResponseEntity<String>> ingestAndSend() {
        logger.info("Triggered ingestAndSend");

        ObjectNode catFactNode = objectMapper.createObjectNode();

        return entityService.addItem("CatFact", ENTITY_VERSION, catFactNode, processCatFact)
                .thenCompose(catFactId -> entityService.getItems("Subscriber", ENTITY_VERSION, null, null))
                .thenApply(subscribers -> {
                    for (JsonNode subscriberNode : subscribers) {
                        String email = subscriberNode.path("email").asText(null);
                        if (email != null) {
                            CompletableFuture.runAsync(() -> {
                                logger.info("Sending cat fact email to {}", email);
                                // TODO: Implement actual email sending here
                            });
                        }
                    }
                    return ResponseEntity.ok("Cat fact ingested and email sending started");
                });
    }

    @PostMapping("/facts/manual-send")
    public CompletableFuture<ResponseEntity<String>> manualSend() {
        logger.info("Manual trigger of weekly cat fact email send");
        String currentWeek = getCurrentIsoWeek();

        return entityService.getItem("CatFact", ENTITY_VERSION, currentWeek)
                .thenCompose(optCatFact -> {
                    if (optCatFact.isEmpty()) {
                        return CompletableFuture.completedFuture(ResponseEntity.status(HttpStatus.NOT_FOUND).body("No cat fact found for current week"));
                    }
                    JsonNode catFact = optCatFact.get();
                    return entityService.getItems("Subscriber", ENTITY_VERSION, null, null)
                            .thenApply(subscribers -> {
                                for (JsonNode subscriberNode : subscribers) {
                                    String email = subscriberNode.path("email").asText(null);
                                    if (email != null) {
                                        CompletableFuture.runAsync(() -> {
                                            logger.info("Sending cat fact email to {}", email);
                                            // TODO: Implement actual email sending here
                                        });
                                    }
                                }
                                return ResponseEntity.ok("Manual email sending started");
                            });
                });
    }

    @GetMapping("/reports/subscribers-count")
    public CompletableFuture<ResponseEntity<Object>> getSubscribersCount() {
        return entityService.getItems("Subscriber", ENTITY_VERSION, null, null)
                .thenApply(subscribers -> {
                    int count = subscribers.size();
                    logger.info("Returning active subscriber count: {}", count);
                    return ResponseEntity.ok(Collections.singletonMap("activeSubscribers", count));
                });
    }

    @GetMapping("/reports/interaction-summary")
    public CompletableFuture<ResponseEntity<ObjectNode>> getInteractionSummary(
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startDate,
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endDate) {
        String currentWeek = getCurrentIsoWeek();

        return entityService.getItem("InteractionSummary", ENTITY_VERSION, currentWeek)
                .thenApply(optSummary -> {
                    ObjectNode summary = optSummary.map(node -> (ObjectNode) node)
                            .orElseGet(() -> {
                                ObjectNode empty = objectMapper.createObjectNode();
                                empty.put("emailOpens", 0);
                                empty.put("emailClicks", 0);
                                return empty;
                            });
                    logger.info("Returning interaction summary for week {}: {}", currentWeek, summary);
                    return ResponseEntity.ok(summary);
                });
    }

    @PostMapping("/webhook/email-interaction")
    public CompletableFuture<ResponseEntity<String>> emailInteractionWebhook(@RequestBody @Valid EmailInteraction interaction) {
        logger.info("Received email interaction webhook: {}", interaction);

        ObjectNode interactionNode = objectMapper.valueToTree(interaction);

        return entityService.addItem("EmailInteraction", ENTITY_VERSION, interactionNode, processEmailInteraction)
                .thenApply(id -> ResponseEntity.ok("Interaction recorded"));
    }

    // Utility methods

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            logger.info("Invalid email provided: {}", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address");
        }
    }

    private String getCurrentIsoWeek() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        // ISO week date format YYYY-Www
        return now.format(DateTimeFormatter.ISO_WEEK_DATE).substring(0, 8);
    }

    // DTOs

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
    public static class EmailInteraction {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "open|click")
        private String eventType;

        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
        private String timestamp;
    }
}