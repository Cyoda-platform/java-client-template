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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, CatFact> sentFacts = new ConcurrentHashMap<>();
    private final Map<UUID, InteractionMetrics> metrics = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    // 1. User Subscription - POST /api/subscribers
    @PostMapping("/subscribers")
    public ResponseEntity<SubscriptionResponse> subscribeUser(@RequestBody SubscriptionRequest request) {
        log.info("Received subscription request for email={}", request.getEmail());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        // Simple duplicate check by email
        Optional<Subscriber> existing = subscribers.values().stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(request.getEmail()))
                .findAny();
        if (existing.isPresent()) {
            log.info("Email {} already subscribed", request.getEmail());
            return ResponseEntity.ok(new SubscriptionResponse(existing.get().getId(), "Already subscribed"));
        }

        UUID subscriberId = UUID.randomUUID();
        Subscriber sub = new Subscriber(subscriberId, request.getEmail(), request.getName(), Instant.now());
        subscribers.put(subscriberId, sub);
        metrics.putIfAbsent(subscriberId, new InteractionMetrics(0, 0, 0));
        log.info("User subscribed with id={}", subscriberId);
        return ResponseEntity.ok(new SubscriptionResponse(subscriberId, "Subscription successful"));
    }

    // 2. Retrieve Subscriber List - GET /api/subscribers
    @GetMapping("/subscribers")
    public List<Subscriber> getSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        return new ArrayList<>(subscribers.values());
    }

    // 3. Trigger Weekly Cat Fact Retrieval and Email Send-Out - POST /api/facts/send-weekly
    @PostMapping("/facts/send-weekly")
    public ResponseEntity<SendFactResponse> sendWeeklyFact() {
        log.info("Triggered weekly cat fact retrieval and email send-out");
        JsonNode factJson;

        try {
            // Call external Cat Fact API
            String response = restTemplate.getForObject(new URI(CAT_FACT_API_URL), String.class);
            factJson = objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to fetch cat fact from external API: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch cat fact");
        }

        String factText = factJson.path("fact").asText(null);
        if (factText == null) {
            log.error("Cat fact not found in API response");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid cat fact response");
        }

        UUID factId = UUID.randomUUID();
        CatFact fact = new CatFact(factId, factText, Instant.now());
        sentFacts.put(factId, fact);

        // Fire-and-forget sending emails
        sendEmailsToSubscribersAsync(fact);

        int subscriberCount = subscribers.size();
        log.info("Weekly cat fact '{}' prepared for sending to {} subscribers", factText, subscriberCount);

        return ResponseEntity.ok(new SendFactResponse(factId, factText, subscriberCount));
    }

    @Async
    public CompletableFuture<Void> sendEmailsToSubscribersAsync(CatFact fact) {
        // TODO: Replace this mock with actual email sending logic
        subscribers.values().forEach(sub -> {
            logger.info("Sending email to {}: {}", sub.getEmail(), fact.getFact());
            // Simulate interaction metrics update (opens, clicks) randomly for prototype
            InteractionMetrics m = metrics.get(sub.getId());
            if (m != null) {
                m.incrementEmailsSent();
                // For prototype, randomly simulate open/click rates
                if (new Random().nextBoolean()) m.incrementEmailOpens();
                if (new Random().nextInt(10) > 7) m.incrementLinkClicks();
            }
        });
        // Fire-and-forget so no blocking here
        return CompletableFuture.completedFuture(null);
    }

    // 4. Retrieve Reporting Metrics - GET /api/reporting/metrics
    @GetMapping("/reporting/metrics")
    public ReportingMetrics getReportingMetrics() {
        int totalSubscribers = subscribers.size();
        int totalEmailsSent = metrics.values().stream().mapToInt(InteractionMetrics::getEmailsSent).sum();
        int totalOpens = metrics.values().stream().mapToInt(InteractionMetrics::getEmailOpens).sum();
        int totalClicks = metrics.values().stream().mapToInt(InteractionMetrics::getLinkClicks).sum();

        double averageOpenRate = totalEmailsSent > 0 ? (double) totalOpens / totalEmailsSent : 0.0;
        double averageClickRate = totalEmailsSent > 0 ? (double) totalClicks / totalEmailsSent : 0.0;

        log.info("Reporting metrics computed: subscribers={}, emailsSent={}, openRate={}, clickRate={}",
                totalSubscribers, totalEmailsSent, averageOpenRate, averageClickRate);

        return new ReportingMetrics(totalSubscribers, totalEmailsSent, averageOpenRate, averageClickRate);
    }

    // Minimal error handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // --- DTOs and Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionRequest {
        private String email;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class SubscriptionResponse {
        private UUID subscriberId;
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private UUID id;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class CatFact {
        private UUID factId;
        private String fact;
        private Instant createdAt;
    }

    @Data
    @NoArgsConstructor
    public static class InteractionMetrics {
        private int emailsSent;
        private int emailOpens;
        private int linkClicks;

        public InteractionMetrics(int emailsSent, int emailOpens, int linkClicks) {
            this.emailsSent = emailsSent;
            this.emailOpens = emailOpens;
            this.linkClicks = linkClicks;
        }

        public void incrementEmailsSent() {
            this.emailsSent++;
        }

        public void incrementEmailOpens() {
            this.emailOpens++;
        }

        public void incrementLinkClicks() {
            this.linkClicks++;
        }
    }

    @Data
    @AllArgsConstructor
    public static class SendFactResponse {
        private UUID factId;
        private String factText;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    public static class ReportingMetrics {
        private int totalSubscribers;
        private int emailsSent;
        private double averageOpenRate;
        private double averageClickRate;
    }
}
```