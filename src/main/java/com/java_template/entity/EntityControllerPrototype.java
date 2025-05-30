```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory subscriber store: subscriberId -> Subscriber
    private final Map<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();
    // Map email to UUID for duplicate prevention
    private final Map<String, UUID> emailToId = new ConcurrentHashMap<>();

    // Store latest cat fact sent
    private volatile CatFact latestCatFact;

    // Interaction tracking: simple count maps
    private final Map<String, Integer> interactionCounts = new ConcurrentHashMap<>();

    /**
     * POST /subscribers
     * Register a new subscriber by email
     */
    @PostMapping("/subscribers")
    public ResponseEntity<SubscriberResponse> registerSubscriber(@Valid @RequestBody SubscriberRequest request) {
        logger.info("Registering subscriber with email={}", request.getEmail());

        if (emailToId.containsKey(request.getEmail().toLowerCase(Locale.ROOT))) {
            UUID existingId = emailToId.get(request.getEmail().toLowerCase(Locale.ROOT));
            Subscriber existing = subscribers.get(existingId);
            logger.info("Subscriber already exists with id={}", existingId);
            return ResponseEntity.ok(new SubscriberResponse(existing.getId(), existing.getEmail(), "subscribed"));
        }

        UUID newId = UUID.randomUUID();
        Subscriber sub = new Subscriber(newId, request.getEmail().toLowerCase(Locale.ROOT));
        subscribers.put(newId, sub);
        emailToId.put(request.getEmail().toLowerCase(Locale.ROOT), newId);

        logger.info("Subscriber registered with id={}", newId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubscriberResponse(newId, request.getEmail(), "subscribed"));
    }

    /**
     * GET /subscribers/count
     * Returns total subscriber count
     */
    @GetMapping("/subscribers/count")
    public Map<String, Integer> getSubscriberCount() {
        int count = subscribers.size();
        logger.info("Returning subscriber count={}", count);
        return Collections.singletonMap("count", count);
    }

    /**
     * POST /catfact/fetch-and-send
     * Fetch new cat fact from external API and send emails (mocked)
     */
    @PostMapping("/catfact/fetch-and-send")
    public ResponseEntity<CatFactResponse> fetchAndSendCatFact() {
        logger.info("Triggered fetch and send cat fact");

        // Fetch cat fact from external API
        String url = "https://catfact.ninja/fact";
        JsonNode rootNode;
        try {
            String json = restTemplate.getForObject(url, String.class);
            rootNode = objectMapper.readTree(json);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }

        String factText = rootNode.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            logger.error("Cat fact missing in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cat fact missing in response");
        }

        UUID factId = UUID.randomUUID();
        Instant now = Instant.now();
        CatFact catFact = new CatFact(factId, factText, now);
        latestCatFact = catFact;

        int emailsSent = subscribers.size();

        // Fire-and-forget email sending simulation
        CompletableFuture.runAsync(() -> sendEmails(catFact, subscribers.values()));

        logger.info("Cat fact fetched and email sending started to {} subscribers", emailsSent);

        return ResponseEntity.ok(new CatFactResponse(factId, factText, emailsSent));
    }

    /**
     * GET /catfact/latest
     * Return the latest cat fact sent
     */
    @GetMapping("/catfact/latest")
    public ResponseEntity<CatFactResponseWithDate> getLatestCatFact() {
        if (latestCatFact == null) {
            logger.info("No cat fact sent yet");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new CatFactResponseWithDate(
                latestCatFact.getId(),
                latestCatFact.getFact(),
                latestCatFact.getSentDate().toString()));
    }

    /**
     * POST /interactions
     * Record user interaction with a cat fact email
     */
    @PostMapping("/interactions")
    public Map<String, String> recordInteraction(@Valid @RequestBody InteractionRequest request) {
        logger.info("Recording interaction: subscriberId={}, factId={}, type={}",
                request.getSubscriberId(), request.getFactId(), request.getInteractionType());

        // Validate subscriber exists
        if (!subscribers.containsKey(request.getSubscriberId())) {
            logger.error("Subscriber id not found: {}", request.getSubscriberId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        // Validate interactionType
        if (!("open".equalsIgnoreCase(request.getInteractionType()) || "click".equalsIgnoreCase(request.getInteractionType()))) {
            logger.error("Invalid interactionType: {}", request.getInteractionType());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid interactionType");
        }

        // TODO: You may want to validate factId matches latestCatFact or store all facts

        // Key: interactionType, increment count
        String key = request.getInteractionType().toLowerCase(Locale.ROOT);
        interactionCounts.merge(key, 1, Integer::sum);

        logger.info("Interaction recorded for type={}", key);

        return Collections.singletonMap("status", "recorded");
    }

    /**
     * GET /interactions/stats
     * Return aggregated interaction stats
     */
    @GetMapping("/interactions/stats")
    public InteractionStatsResponse getInteractionStats() {
        int totalOpens = interactionCounts.getOrDefault("open", 0);
        int totalClicks = interactionCounts.getOrDefault("click", 0);
        logger.info("Returning interaction stats: opens={}, clicks={}", totalOpens, totalClicks);
        return new InteractionStatsResponse(totalOpens, totalClicks);
    }

    /**
     * Simulates sending emails to subscribers with the cat fact.
     * TODO: Replace with real email sending logic.
     */
    @Async
    public void sendEmails(CatFact catFact, Collection<Subscriber> subscribers) {
        logger.info("Simulating sending emails to {} subscribers", subscribers.size());
        try {
            // Simulate delay
            Thread.sleep(1000);
            // TODO: Implement real email sending here
            logger.info("Emails sent with cat fact: {}", catFact.getFact());
        } catch (InterruptedException e) {
            logger.error("Email sending simulation interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    // === Exception handling ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // === DTOs and Entities ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SubscriberRequest {
        private String email;
    }

    @Data
    @AllArgsConstructor
    private static class SubscriberResponse {
        private UUID subscriberId;
        private String email;
        private String status;
    }

    @Data
    @AllArgsConstructor
    private static class CatFactResponse {
        private UUID factId;
        private String catFact;
        private int emailsSent;
    }

    @Data
    @AllArgsConstructor
    private static class CatFactResponseWithDate {
        private UUID factId;
        private String catFact;
        private String sentDate;
    }

    @Data
    @AllArgsConstructor
    private static class InteractionRequest {
        private UUID subscriberId;
        private UUID factId;
        private String interactionType; // "open" or "click"
    }

    @Data
    @AllArgsConstructor
    private static class InteractionStatsResponse {
        private int totalOpens;
        private int totalClicks;
    }

    @Data
    @AllArgsConstructor
    private static class Subscriber {
        private UUID id;
        private String email;
    }

    @Data
    @AllArgsConstructor
    private static class CatFact {
        private UUID id;
        private String fact;
        private Instant sentDate;
    }
}
```
