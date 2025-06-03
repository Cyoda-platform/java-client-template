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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, CatFact> catFacts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple counters to simulate email sent stats and facts sent count
    private final AtomicInteger emailsSentCounter = new AtomicInteger(0);
    private final AtomicInteger factsSentCounter = new AtomicInteger(0);

    private final RestTemplate restTemplate = new RestTemplate();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // === Models ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String id;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatFact {
        private String factId;
        private String fact;
        private Instant timestamp;
    }

    @Data
    static class SubscriptionRequest {
        private String email;
        private String name;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
        private String subscriberId;
    }

    @Data
    @AllArgsConstructor
    static class SendWeeklyResponse {
        private String message;
        private String factId;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    static class SubscriberCountResponse {
        private int totalSubscribers;
    }

    @Data
    @AllArgsConstructor
    static class InteractionReportResponse {
        private int factsSent;
        private int emailsSent;
    }

    // === Endpoint: Subscribe user ===
    @PostMapping(value = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse subscribeUser(@RequestBody SubscriptionRequest request) {
        log.info("Received subscription request for email={}", request.getEmail());

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            log.error("Subscription failed: email is missing");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }

        // Simple ID generation (UUID preferred in real)
        String subscriberId = "sub-" + Instant.now().toEpochMilli();

        Subscriber subscriber = new Subscriber(subscriberId, request.getEmail(), request.getName(), Instant.now());
        subscribers.put(subscriberId, subscriber);

        log.info("Subscriber {} added successfully", subscriberId);

        return new MessageResponse("Subscription successful", subscriberId);
    }

    // === Endpoint: Trigger weekly fetch and send cat fact ===
    @PostMapping(value = "/facts/sendWeekly", produces = MediaType.APPLICATION_JSON_VALUE)
    public SendWeeklyResponse sendWeeklyCatFact() {
        log.info("Triggered weekly cat fact fetch and sending");

        JsonNode catFactJson = fetchCatFactFromExternalApi();

        if (catFactJson == null || !catFactJson.has("fact")) {
            log.error("Failed to fetch valid cat fact from external API");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch cat fact");
        }

        String factText = catFactJson.get("fact").asText();

        String factId = "fact-" + Instant.now().toEpochMilli();
        CatFact catFact = new CatFact(factId, factText, Instant.now());
        catFacts.put(factId, catFact);

        factsSentCounter.incrementAndGet();

        List<Subscriber> allSubscribers = new ArrayList<>(subscribers.values());

        // Fire-and-forget email sending (TODO: replace with real async email sending)
        CompletableFuture.runAsync(() -> sendEmailsToSubscribers(catFact, allSubscribers), executor);

        return new SendWeeklyResponse("Weekly cat fact retrieved and emails sent", factId, allSubscribers.size());
    }

    private JsonNode fetchCatFactFromExternalApi() {
        try {
            URI uri = new URI("https://catfact.ninja/fact");
            String response = restTemplate.getForObject(uri, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Error fetching cat fact from external API", e);
            return null;
        }
    }

    private void sendEmailsToSubscribers(CatFact catFact, List<Subscriber> subscribers) {
        log.info("Sending cat fact emails to {} subscribers", subscribers.size());

        // TODO: Replace this mock with actual email sending logic
        for (Subscriber sub : subscribers) {
            try {
                // Simulate email sending delay
                Thread.sleep(10);
                log.info("Sent cat fact to subscriberId={}, email={}", sub.getId(), sub.getEmail());
                emailsSentCounter.incrementAndGet();
            } catch (InterruptedException e) {
                log.error("Error sending email to subscriberId={}", sub.getId(), e);
                Thread.currentThread().interrupt();
            }
        }

        log.info("Finished sending cat fact emails");
    }

    // === Endpoint: Retrieve stored cat facts ===
    @GetMapping(value = "/facts", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CatFact> getStoredCatFacts() {
        log.info("Retrieving all stored cat facts");
        return new ArrayList<>(catFacts.values());
    }

    // === Endpoint: Reporting subscriber count ===
    @GetMapping(value = "/report/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriberCountResponse getSubscriberCount() {
        int count = subscribers.size();
        log.info("Reporting total subscribers: {}", count);
        return new SubscriberCountResponse(count);
    }

    // === Endpoint: Reporting interactions ===
    @GetMapping(value = "/report/interactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public InteractionReportResponse getInteractionReport() {
        int factsSent = factsSentCounter.get();
        int emailsSent = emailsSentCounter.get();
        log.info("Reporting interactions: factsSent={}, emailsSent={}", factsSent, emailsSent);
        return new InteractionReportResponse(factsSent, emailsSent);
    }

    // === Basic exception handler for ResponseStatusException ===
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("API error: {} - {}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

}
```
