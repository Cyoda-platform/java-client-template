```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, InteractionReport> interactionReports = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized.");
    }

    // --------- Subscriber API ---------

    @PostMapping("/subscribers")
    public ResponseEntity<Subscriber> createSubscriber(@RequestBody SubscriberRequest request) {
        log.info("Received subscriber creation request for email: {}", request.getEmail());

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be blank");
        }

        // Simple uniqueness check by email (inefficient, but OK for prototype)
        boolean alreadyExists = subscribers.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(request.getEmail()));
        if (alreadyExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscriber with this email already exists");
        }

        String id = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(id, request.getEmail(), "subscribed");
        subscribers.put(id, subscriber);

        log.info("Subscriber created with id: {}", id);
        return ResponseEntity.created(URI.create("/api/subscribers/" + id)).body(subscriber);
    }

    @GetMapping("/subscribers/{subscriberId}")
    public ResponseEntity<Subscriber> getSubscriber(@PathVariable String subscriberId) {
        Subscriber subscriber = subscribers.get(subscriberId);
        if (subscriber == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        log.info("Returning subscriber info for id: {}", subscriberId);
        return ResponseEntity.ok(subscriber);
    }

    // --------- Weekly Cat Fact Ingestion and Email Sending ---------

    @PostMapping("/facts/sendWeekly")
    public ResponseEntity<FactSendResponse> sendWeeklyCatFact() {
        log.info("Triggering weekly cat fact ingestion and email sending.");

        JsonNode catFactJson;
        try {
            String responseString = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            catFactJson = objectMapper.readTree(responseString);
        } catch (Exception e) {
            log.error("Failed to retrieve cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve cat fact");
        }

        String catFactText = catFactJson.path("fact").asText(null);
        if (catFactText == null) {
            log.error("Cat fact missing in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid cat fact response");
        }

        int subscriberCount = subscribers.size();

        // Fire and forget sending emails - TODO replace with real async email sending logic
        CompletableFuture.runAsync(() -> sendCatFactEmails(catFactText));

        FactSendResponse response = new FactSendResponse(subscriberCount, catFactText);
        log.info("Cat fact sent to {} subscribers", subscriberCount);
        return ResponseEntity.ok(response);
    }

    @Async
    void sendCatFactEmails(String catFactText) {
        // TODO: Replace this mock with real email sending service integration
        log.info("Sending cat fact emails to {} subscribers...", subscribers.size());
        subscribers.values().forEach(subscriber -> {
            // Simulate email sending
            log.info("Sending email to {}: {}", subscriber.getEmail(), catFactText);
        });
        // Optionally update interactionReports or logs here
    }

    // --------- Reporting ---------

    @GetMapping("/report/subscribers")
    public ResponseEntity<SubscriberReport> getSubscriberReport() {
        int total = subscribers.size();
        log.info("Subscriber report requested, total: {}", total);
        return ResponseEntity.ok(new SubscriberReport(total));
    }

    @GetMapping("/report/interaction")
    public ResponseEntity<InteractionReport> getInteractionReport() {
        // TODO: Replace with real interaction tracking logic
        int emailOpens = interactionReports.values().stream().mapToInt(InteractionReport::getEmailOpens).sum();
        int linkClicks = interactionReports.values().stream().mapToInt(InteractionReport::getLinkClicks).sum();

        InteractionReport report = new InteractionReport(emailOpens, linkClicks);
        log.info("Interaction report requested: opens={}, clicks={}", emailOpens, linkClicks);
        return ResponseEntity.ok(report);
    }

    // --------- Exception Handling ---------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error occurred"));
    }

    // --------- DTOs and Models ---------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        private String email;
    }

    @Data
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
