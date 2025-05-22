```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api-cyoda")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    // Keep in-memory caches for subscribers and tracking:
    private final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> factOpenTracking = new ConcurrentHashMap<>();
    private volatile String lastFactId = null;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function for subscriber entity.
     * Handles asynchronous tasks like normalizing email and updating cache.
     *
     * @param entity ObjectNode representing subscriber entity.
     * @return CompletableFuture with modified ObjectNode.
     */
    private CompletableFuture<Object> processSubscriber(Object entity) {
        if (!(entity instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entity);
        }
        ObjectNode subscriberNode = (ObjectNode) entity;

        // Normalize email to lowercase and trim
        String email = subscriberNode.path("email").asText(null);
        if (email != null) {
            email = email.toLowerCase().trim();
            subscriberNode.put("email", email);
        }

        // Update in-memory subscribers cache
        boolean active = subscriberNode.path("active").asBoolean(false);

        // Since entityService add/update is asynchronous, this workflow is async too
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Refresh the subscribers cache from entityService to keep consistency
                ArrayNode subsArray = entityService.getItems("subscriber", ENTITY_VERSION).get();
                subscribers.clear();
                for (JsonNode node : subsArray) {
                    Subscriber s = objectMapper.convertValue(node, Subscriber.class);
                    subscribers.put(s.getEmail(), s);
                }
            } catch (Exception e) {
                logger.error("Failed to refresh subscribers cache in workflow", e);
            }

            // Add or update current subscriber cache entry - overwrite or insert
            if (email != null) {
                subscribers.put(email, new Subscriber(email, active));
                logger.info("Workflow updated subscribers cache for email: {}", email);
            }
            return subscriberNode;
        });
    }

    /**
     * Workflow function for fact entity.
     * Fetches cat fact from API, sends emails asynchronously to subscribers,
     * and updates in-memory lastFactId and tracking map.
     *
     * @param entity ObjectNode representing fact entity.
     * @return CompletableFuture with modified ObjectNode.
     */
    private CompletableFuture<Object> processFact(Object entity) {
        if (!(entity instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entity);
        }
        ObjectNode factNode = (ObjectNode) entity;

        // Extract or generate factId (should exist)
        String factId = factNode.path("factId").asText(null);
        if (factId == null || factId.isEmpty()) {
            factId = UUID.randomUUID().toString();
            factNode.put("factId", factId);
        }
        lastFactId = factId;

        // Fetch cat fact text if missing or empty (should normally be present)
        String factText = factNode.path("fact").asText(null);

        // If factText missing, fetch synchronously here — but ideally fact provided by controller
        if (factText == null || factText.isEmpty()) {
            try {
                String catFactApiUrl = "https://catfact.ninja/fact";
                String json = restTemplate.getForObject(catFactApiUrl, String.class);
                JsonNode root = objectMapper.readTree(json);
                factText = root.path("fact").asText(null);
                if (factText == null || factText.isEmpty()) {
                    logger.error("Cat fact API returned no fact in workflow");
                    throw new RuntimeException("Failed to retrieve cat fact");
                }
                factNode.put("fact", factText);
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact in workflow", e);
                // Let the entity persist even if fact fetch failed
                return CompletableFuture.completedFuture(factNode);
            }
        }

        // Clear or initialize tracking for this fact ID
        factOpenTracking.putIfAbsent(factId, new ConcurrentSkipListSet<>());

        // Send emails asynchronously (fire-and-forget)
        CompletableFuture.runAsync(() -> {
            Set<Map.Entry<String, Subscriber>> entries = subscribers.entrySet();
            for (Map.Entry<String, Subscriber> entry : entries) {
                Subscriber sub = entry.getValue();
                if (sub.isActive()) {
                    try {
                        sendEmail(sub.getEmail(), factId, factText);
                    } catch (Exception e) {
                        logger.error("Failed to send email to {}", sub.getEmail(), e);
                    }
                }
            }
        });

        return CompletableFuture.completedFuture(factNode);
    }

    // --- Controllers simplified: all async tasks removed ---

    @PostMapping("/subscribers")
    public ResponseEntity<?> subscribeUser(@RequestBody @Valid EmailRequest emailRequest) throws ExecutionException, InterruptedException {
        String email = emailRequest.getEmail().toLowerCase().trim();
        Subscriber existing = subscribers.get(email);
        if (existing == null) {
            Subscriber newSub = new Subscriber(email, true);
            UUID technicalId = entityService.addItem("subscriber", ENTITY_VERSION, newSub, this::processSubscriber).get();
            logger.info("User subscribed: {} with technicalId {}", email, technicalId);
        } else if (!existing.isActive()) {
            existing.setActive(true);
            UUID technicalId = getTechnicalIdByEmail(email);
            entityService.updateItem("subscriber", ENTITY_VERSION, technicalId, existing).get();
            logger.info("User re-subscribed: {} with technicalId {}", email, technicalId);
        } else {
            logger.info("User already subscribed: {}", email);
        }
        return ResponseEntity.ok(Map.of("message", "Subscription successful"));
    }

    @PostMapping("/subscribers/unsubscribe")
    public ResponseEntity<?> unsubscribeUser(@RequestBody @Valid EmailRequest emailRequest) throws ExecutionException, InterruptedException {
        String email = emailRequest.getEmail().toLowerCase().trim();
        Subscriber sub = subscribers.get(email);
        if (sub == null || !sub.isActive()) {
            logger.info("Unsubscribe attempt for non-existing or inactive email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Email not subscribed"));
        }
        sub.setActive(false);
        UUID technicalId = getTechnicalIdByEmail(email);
        entityService.updateItem("subscriber", ENTITY_VERSION, technicalId, sub).get();
        logger.info("User unsubscribed: {}", email);
        return ResponseEntity.ok(Map.of("message", "Unsubscribed successfully"));
    }

    @PostMapping("/facts/send-weekly")
    public ResponseEntity<?> sendWeeklyFact() throws ExecutionException, InterruptedException, IOException {
        logger.info("Triggered weekly cat fact fetch and email send");

        // Fetch fact from API here
        String catFactApiUrl = "https://catfact.ninja/fact";
        String json = restTemplate.getForObject(catFactApiUrl, String.class);
        JsonNode root = objectMapper.readTree(json);
        String fact = root.path("fact").asText(null);
        if (fact == null || fact.isEmpty()) {
            logger.error("Cat fact API returned no fact");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to retrieve cat fact");
        }

        // Create fact entity with new UUID
        Fact factEntity = new Fact(UUID.randomUUID().toString(), fact);

        // Add with workflow, which will send emails asynchronously and update caches
        UUID factTechnicalId = entityService.addItem("fact", ENTITY_VERSION, factEntity, this::processFact).get();

        return ResponseEntity.ok(Map.of("message", "Weekly cat fact sent to subscribers"));
    }

    @GetMapping(value = "/facts/track-open/{emailEncoded}/{factId}", produces = MediaType.IMAGE_PNG_VALUE)
    public void trackEmailOpen(@PathVariable String emailEncoded, @PathVariable String factId, HttpServletResponse response) {
        try {
            String email = new String(Base64Utils.decodeFromUrlSafeString(emailEncoded));
            factOpenTracking.computeIfAbsent(factId, k -> new ConcurrentSkipListSet<>()).add(email);
            logger.info("Tracked open for factId {} and email {}", factId, email);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid base64 email encoding in tracking pixel request");
        }
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try {
            byte[] pixel = new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x06,0x00,0x00,0x00,0x1F,0x15,(byte)0xC4,(byte)0x89,0x00,0x00,0x00,0x0A,0x49,0x44,0x41,0x54,0x78,(byte)0xDA,0x63,0x00,0x01,0x00,0x00,0x05,0x00,0x01,0x0D,0x0A,0x2D,(byte)0xB4,0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,(byte)0xAE,0x42,0x60,(byte)0x82};
            response.getOutputStream().write(pixel);
            response.getOutputStream().flush();
        } catch (IOException e) {
            logger.error("Failed to write tracking pixel", e);
        }
    }

    @GetMapping("/report/summary")
    public ResponseEntity<?> getReportSummary() throws ExecutionException, InterruptedException {
        // Refresh subscribers from entityService to keep in sync
        ArrayNode subsArray = entityService.getItems("subscriber", ENTITY_VERSION).get();
        subscribers.clear();
        for (JsonNode node : subsArray) {
            Subscriber s = objectMapper.convertValue(node, Subscriber.class);
            subscribers.put(s.getEmail(), s);
        }

        int totalSubscribers = subscribers.size();
        long activeSubscribers = subscribers.values().stream().filter(Subscriber::isActive).count();
        int emailsSent = lastFactId == null ? 0 : (int) activeSubscribers;
        int emailOpens = lastFactId == null ? 0 : factOpenTracking.getOrDefault(lastFactId, new ConcurrentSkipListSet<>()).size();

        logger.info("Report requested: total={}, active={}, sent={}, opens={}", totalSubscribers, activeSubscribers, emailsSent, emailOpens);
        return ResponseEntity.ok(Map.of(
                "totalSubscribers", totalSubscribers,
                "activeSubscribers", activeSubscribers,
                "emailsSentThisWeek", emailsSent,
                "emailOpensThisWeek", emailOpens
        ));
    }

    private UUID getTechnicalIdByEmail(String email) throws ExecutionException, InterruptedException {
        String condition = String.format("{\"email\":\"%s\"}", email);
        ArrayNode filtered = entityService.getItemsByCondition("subscriber", ENTITY_VERSION, condition).get();
        if (filtered.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        return UUID.fromString(filtered.get(0).get("technicalId").asText());
    }

    private void sendEmail(String email, String factId, String fact) {
        String emailEncoded = Base64Utils.encodeToUrlSafeString(email.getBytes());
        String trackingPixelUrl = String.format("http://localhost:8080/api-cyoda/facts/track-open/%s/%s", emailEncoded, factId);
        String unsubscribeUrl = "http://localhost:8080/unsubscribe?email=" + email; // TODO: replace with real URL
        logger.info("Sending email to {} with fact: {}", email, fact);
        logger.info("Include tracking pixel URL: {}", trackingPixelUrl);
        logger.info("Include unsubscribe URL: {}", unsubscribeUrl);
        // TODO: integrate with real email service provider
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EmailRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private boolean active;
    }

    @Data
    @AllArgsConstructor
    static class Fact {
        private String factId;
        private String fact;
    }
}
```
---

### What was moved and why?

- **Subscriber workflow (`processSubscriber`)**:
  - Normalizes email (lowercase + trim).
  - Updates the in-memory `subscribers` cache asynchronously by fetching all subscriber items from `entityService`.
  - This frees controllers from manually updating cache or normalizing input.

- **Fact workflow (`processFact`)**:
  - Ensures `factId` is set.
  - If fact text is missing, fetches a cat fact (fallback).
  - Updates `lastFactId` and initializes tracking map.
  - Sends emails asynchronously to all active subscribers.
  - This moves the entire "fetch fact + send emails" async logic out of the controller.

- **Controller methods** now only handle HTTP requests, validation, and call `addItem/updateItem` with workflow functions.

- **Tracking pixel and report endpoints** remain unchanged, as they are primarily read operations and tracking is done on HTTP request.

---

### Benefits

- Separation of concerns: controllers handle inputs/outputs; workflows handle business logic and async tasks.
- Async tasks consolidated in one place, improving maintainability.
- Avoids infinite recursion because workflows do not add/update/delete their own entity model.
- Easier to test and evolve workflows independently from controller routing.

---

If you want me to also move `updateItem` logic into workflows or apply similar patterns for other entities or actions, just ask!