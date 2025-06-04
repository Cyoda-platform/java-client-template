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

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@Slf4j
public class EntityControllerPrototype {

    private final Map<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, List<Interaction>> interactions = new ConcurrentHashMap<>();
    private final List<SentFact> sentFacts = Collections.synchronizedList(new ArrayList<>());

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    // ========== Models ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private UUID subscriberId;
        private String email;
        private String status; // e.g. "subscribed"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatFactResponse {
        private String fact;
        private int length;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SentFact {
        private String fact;
        private Instant timestamp;
        private int sentCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Interaction {
        private UUID subscriberId;
        private String interactionType; // "open" or "click"
        private Instant timestamp;
    }

    @Data
    static class SignUpRequest {
        private String email;
    }

    @Data
    static class InteractionRequest {
        private UUID subscriberId;
        private String interactionType; // "open" or "click"
        private Instant timestamp;
    }

    // ========== API Endpoints ==========

    @PostMapping("/subscribers")
    public Subscriber signUp(@RequestBody SignUpRequest request) {
        log.info("Received sign-up request: email={}", request.getEmail());
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be provided");
        }
        // Simple duplicate check:
        boolean exists = subscribers.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(request.getEmail()));
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already subscribed");
        }

        Subscriber subscriber = new Subscriber(UUID.randomUUID(), request.getEmail(), "subscribed");
        subscribers.put(subscriber.getSubscriberId(), subscriber);
        log.info("Subscriber created: id={}, email={}", subscriber.getSubscriberId(), subscriber.getEmail());
        return subscriber;
    }

    @PostMapping("/facts/sendWeekly")
    public Map<String, Object> sendWeeklyFact() {
        log.info("Triggered weekly cat fact retrieval and email send");

        // Fetch cat fact from external API
        String factText;
        try {
            String response = restTemplate.getForObject(new URI(CAT_FACT_API_URL), String.class);
            JsonNode root = objectMapper.readTree(response);
            factText = root.path("fact").asText(null);
            if (factText == null) {
                throw new Exception("Fact field missing in API response");
            }
            log.info("Fetched cat fact: {}", factText);
        } catch (Exception ex) {
            log.error("Failed to fetch cat fact", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }

        int sentCount = subscribers.size();

        // Fire-and-forget email sending simulation & logging
        CompletableFuture.runAsync(() -> {
            log.info("Simulating sending emails to {} subscribers", sentCount);

            // TODO: Replace with real email sending logic via chosen provider
            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException ignored) {}

            // Log sent fact
            SentFact sentFact = new SentFact(factText, Instant.now(), sentCount);
            sentFacts.add(sentFact);

            log.info("Emails sent with cat fact at {}", sentFact.getTimestamp());
        });

        Map<String, Object> response = new HashMap<>();
        response.put("sentCount", sentCount);
        response.put("catFact", factText);
        response.put("timestamp", Instant.now().toString());

        return response;
    }

    @GetMapping("/subscribers")
    public Collection<Subscriber> getSubscribers() {
        log.info("Retrieving all subscribers");
        return subscribers.values();
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> getReportSummary() {
        log.info("Generating report summary");
        int totalSubscribers = subscribers.size();
        int totalFactsSent = sentFacts.size();

        long totalOpens = interactions.values().stream()
                .flatMap(Collection::stream)
                .filter(i -> "open".equalsIgnoreCase(i.getInteractionType()))
                .count();

        long totalClicks = interactions.values().stream()
                .flatMap(Collection::stream)
                .filter(i -> "click".equalsIgnoreCase(i.getInteractionType()))
                .count();

        Instant lastSent = sentFacts.stream()
                .map(SentFact::getTimestamp)
                .max(Instant::compareTo)
                .orElse(null);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSubscribers", totalSubscribers);
        summary.put("totalFactsSent", totalFactsSent);
        summary.put("totalEmailOpens", totalOpens);
        summary.put("totalEmailClicks", totalClicks);
        summary.put("lastFactSentAt", lastSent != null ? lastSent.toString() : null);

        return summary;
    }

    @PostMapping("/interactions")
    public Map<String, Object> trackInteraction(@RequestBody InteractionRequest request) {
        log.info("Tracking interaction: subscriberId={}, type={}, timestamp={}",
                request.getSubscriberId(), request.getInteractionType(), request.getTimestamp());

        if (request.getSubscriberId() == null || request.getInteractionType() == null || request.getTimestamp() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }

        if (!subscribers.containsKey(request.getSubscriberId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }

        String typeLower = request.getInteractionType().toLowerCase(Locale.ROOT);
        if (!typeLower.equals("open") && !typeLower.equals("click")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid interaction type");
        }

        Interaction interaction = new Interaction(request.getSubscriberId(), typeLower, request.getTimestamp());
        interactions.computeIfAbsent(request.getSubscriberId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(interaction);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "recorded");
        resp.put("subscriberId", request.getSubscriberId());
        resp.put("interactionType", typeLower);
        resp.put("timestamp", request.getTimestamp().toString());

        return resp;
    }

    // ========== Basic Exception Handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("status", 500);
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred");
        return error;
    }
}
```
