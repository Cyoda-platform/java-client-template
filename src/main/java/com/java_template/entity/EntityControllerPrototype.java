```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, CatFact> catFacts = new ConcurrentHashMap<>();

    private final Map<String, Integer> emailOpens = new ConcurrentHashMap<>(); // email -> opens count
    private final Map<String, Instant> unsubscribes = new ConcurrentHashMap<>(); // email -> unsubscribe timestamp

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    /**
     * Subscribe a user by email
     */
    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Subscriber subscribe(@RequestBody SubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }
        if (subscribers.containsKey(email)) {
            log.info("Subscribe attempt for existing email: {}", email);
            return subscribers.get(email);
        }
        Subscriber subscriber = new Subscriber(email, Instant.now());
        subscribers.put(email, subscriber);
        log.info("New subscriber added: {}", email);
        return subscriber;
    }

    /**
     * Unsubscribe a user by email immediately
     */
    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> unsubscribe(@RequestBody UnsubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (!subscribers.containsKey(email)) {
            log.warn("Unsubscribe attempt for non-existing email: {}", email);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        subscribers.remove(email);
        unsubscribes.put(email, Instant.now());
        log.info("Unsubscribed: {}", email);
        // TODO: Optionally, cancel any scheduled emails for this user if implemented
        return Map.of("message", "Unsubscribed successfully");
    }

    /**
     * Fetch a new cat fact, store it, and send email to all subscribers
     * Fire-and-forget email sending simulated by async method
     */
    @PostMapping(value = "/catfact/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public CatFactResponse sendWeeklyCatFact() {
        log.info("Starting weekly cat fact fetch and send process");

        JsonNode root;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(new URI(CAT_FACT_API_URL), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to fetch cat fact: status {}", response.getStatusCodeValue());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
            }
            root = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("Exception during cat fact fetch", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
        }

        String factText = root.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            log.error("Cat fact missing in API response");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact missing in API response");
        }

        String factId = String.valueOf(Instant.now().toEpochMilli());
        CatFact catFact = new CatFact(factId, factText, Instant.now());
        catFacts.put(factId, catFact);
        log.info("Stored new cat fact: {}", catFact);

        // Simulate sending emails asynchronously
        CompletableFuture.runAsync(() -> sendEmailsToSubscribers(catFact));

        return new CatFactResponse(factId, factText, catFact.getTimestamp(), subscribers.size());
    }

    @Async
    void sendEmailsToSubscribers(CatFact catFact) {
        // TODO: Replace this stub with actual email sending logic
        // For prototype, just log emails sent
        subscribers.keySet().forEach(email -> {
            log.info("Sending cat fact email to {}", email);
            // TODO: Implement email open tracking pixel in email content
            // Simulate incrementing open count after some time or interaction (mock)
        });
    }

    /**
     * Reporting API - aggregated data only
     */
    @GetMapping(value = "/reporting/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportingSummary getReportingSummary() {
        int totalSubscribers = subscribers.size();
        int totalEmailOpens = emailOpens.values().stream().mapToInt(Integer::intValue).sum();
        int totalUnsubscribes = unsubscribes.size();

        log.info("Reporting summary requested: subscribers={}, opens={}, unsubscribes={}", totalSubscribers, totalEmailOpens, totalUnsubscribes);

        return new ReportingSummary(totalSubscribers, totalEmailOpens, totalUnsubscribes);
    }

    // Basic error handling for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

    // Data classes and DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    static class SubscribeRequest {
        private String email;
    }

    @Data
    static class UnsubscribeRequest {
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class CatFact {
        private String id;
        private String fact;
        private Instant timestamp;
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
