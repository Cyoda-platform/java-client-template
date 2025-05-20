```java
package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Subscriber> unsubscribedSubscribers = new ConcurrentHashMap<>();
    private final Map<String, CatFact> weeklyCatFacts = new ConcurrentHashMap<>();
    private final Map<String, InteractionSummary> weeklyInteractionSummaries = new ConcurrentHashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Workflow function for Subscriber entity
    private Function<Object, Object> processSubscriber = entity -> {
        if (entity instanceof Subscriber subscriber) {
            // Example: add a log or modify subscriber before persisting if needed
            logger.debug("Processing subscriber in workflow: {}", subscriber.getEmail());
            // No modification done here, just return the entity as is
            return subscriber;
        }
        return entity;
    };

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<String>> subscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);
        if (subscribers.containsKey(email)) {
            logger.info("Subscribe attempt failed - email already subscribed: {}", email);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }
        unsubscribedSubscribers.remove(email);
        Subscriber subscriber = new Subscriber(email, Instant.now());

        // Save subscriber via entityService with workflow
        return entityService.addItem("Subscriber", ENTITY_VERSION, subscriber, processSubscriber)
                .thenApply(id -> {
                    subscribers.put(email, subscriber);
                    logger.info("New subscriber added: {}", email);
                    return ResponseEntity.status(HttpStatus.CREATED).body("Subscribed successfully");
                });
    }

    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> unsubscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);
        Subscriber removed = subscribers.remove(email);
        if (removed == null) {
            logger.info("Unsubscribe attempt failed - email not found: {}", email);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found");
        }
        unsubscribedSubscribers.put(email, removed);
        CompletableFuture.runAsync(() -> {
            logger.info("Sending unsubscribe confirmation email to {}", email);
            // TODO: integrate with email service to send confirmation
        });
        logger.info("User unsubscribed: {}", email);
        return ResponseEntity.ok("Unsubscribed successfully");
    }

    @PostMapping(value = "/subscribers/resubscribe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> resubscribe(@RequestBody @Valid SubscriberRequest request) {
        String email = normalizeEmail(request.getEmail());
        validateEmail(email);
        if (subscribers.containsKey(email)) {
            logger.info("Resubscribe attempt failed - user already subscribed: {}", email);
            return ResponseEntity.ok("Already subscribed");
        }
        Subscriber historic = unsubscribedSubscribers.remove(email);
        if (historic == null) {
            logger.info("Resubscribe attempt failed - email never subscribed: {}", email);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found or never subscribed");
        }
        subscribers.put(email, new Subscriber(email, Instant.now()));
        logger.info("User resubscribed: {}", email);
        return ResponseEntity.ok("Resubscribed successfully");
    }

    @PostMapping("/facts/ingest-and-send")
    public ResponseEntity<String> ingestAndSend() throws Exception {
        logger.info("Triggered ingestAndSend");
        CatFact catFact = fetchCatFact();
        String currentWeek = getCurrentIsoWeek();
        weeklyCatFacts.put(currentWeek, catFact);
        logger.info("Stored cat fact for week {}: {}", currentWeek, catFact.getFact());
        CompletableFuture.runAsync(() -> sendEmailToAllSubscribers(catFact));
        return ResponseEntity.ok("Cat fact ingested and email sending started");
    }

    @PostMapping("/facts/manual-send")
    public ResponseEntity<String> manualSend() {
        logger.info("Manual trigger of weekly cat fact email send");
        String currentWeek = getCurrentIsoWeek();
        CatFact catFact = weeklyCatFacts.get(currentWeek);
        if (catFact == null) {
            logger.warn("No cat fact found for current week {}", currentWeek);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No cat fact found for current week");
        }
        CompletableFuture.runAsync(() -> sendEmailToAllSubscribers(catFact));
        return ResponseEntity.ok("Manual email sending started");
    }

    @GetMapping("/reports/subscribers-count")
    public ResponseEntity<Map<String, Integer>> getSubscribersCount() {
        int activeCount = subscribers.size();
        logger.info("Returning active subscriber count: {}", activeCount);
        return ResponseEntity.ok(Collections.singletonMap("activeSubscribers", activeCount));
    }

    @GetMapping("/reports/interaction-summary")
    public ResponseEntity<InteractionSummary> getInteractionSummary(
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String startDate,
            @RequestParam @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String endDate) {
        String currentWeek = getCurrentIsoWeek();
        InteractionSummary summary = weeklyInteractionSummaries.getOrDefault(currentWeek, new InteractionSummary(0, 0));
        logger.info("Returning interaction summary from {} to {}: {}", startDate, endDate, summary);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/webhook/email-interaction")
    public ResponseEntity<String> emailInteractionWebhook(@RequestBody @Valid EmailInteraction interaction) {
        logger.info("Received email interaction webhook: {}", interaction);
        String currentWeek = getCurrentIsoWeek();
        weeklyInteractionSummaries.compute(currentWeek, (week, summary) -> {
            if (summary == null) summary = new InteractionSummary(0, 0);
            switch (interaction.getEventType()) {
                case "open" -> summary.incrementOpens();
                case "click" -> summary.incrementClicks();
                default -> logger.warn("Unknown eventType {}", interaction.getEventType());
            }
            return summary;
        });
        return ResponseEntity.ok("Interaction recorded");
    }

    private CatFact fetchCatFact() throws Exception {
        logger.info("Fetching cat fact from external API");
        String response = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
        var root = objectMapper.readTree(response);
        String fact = root.path("fact").asText(null);
        if (!StringUtils.hasText(fact)) {
            throw new IllegalStateException("Cat fact not found in API response");
        }
        return new CatFact(fact, Instant.now());
    }

    private void sendEmailToAllSubscribers(CatFact catFact) {
        logger.info("Sending cat fact email to {} subscribers", subscribers.size());
        for (Subscriber subscriber : subscribers.values()) {
            CompletableFuture.runAsync(() -> {
                try {
                    logger.info("Sending cat fact email to {}", subscriber.getEmail());
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.error("Email sending interrupted for {}", subscriber.getEmail());
                }
            });
        }
    }

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
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ISO_WEEK_DATE)
                .substring(0, 8);
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
    @AllArgsConstructor
    public static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class CatFact {
        private String fact;
        private Instant retrievedAt;
    }

    @Data
    public static class InteractionSummary {
        private int emailOpens;
        private int emailClicks;

        public InteractionSummary(int opens, int clicks) {
            this.emailOpens = opens;
            this.emailClicks = clicks;
        }

        public void incrementOpens() {
            this.emailOpens++;
        }

        public void incrementClicks() {
            this.emailClicks++;
        }
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
```
---

### Explanation:
- Added a private workflow function `processSubscriber` with the required naming convention `process{EntityName}`.
- This function takes an `Object` (the entity) and returns an `Object` (the possibly modified entity). It checks if the entity is a `Subscriber` and returns it. You can add modification logic inside this function if needed.
- Updated the call to `entityService.addItem(...)` in the `subscribe` method to pass the `processSubscriber` function as the `workflow` argument.
- No other methods required changes since the only entity saved via `addItem` in the provided code is the `Subscriber`.
- Import `java.util.function.Function` is added for the workflow function.

This complies with the new contract of `entityService.addItem` expecting a workflow function applied asynchronously before persistence.