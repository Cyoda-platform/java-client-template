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

import javax.validation.Valid;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private volatile Instant lastFactSentAt = null;
    private volatile String lastFactText = null;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
        private String subscriberId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SendWeeklyResponse {
        private String message;
        private String fact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StatsResponse {
        private int totalSubscribers;
        private Instant lastFactSent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UnsubscribeRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UnsubscribeResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String id;
        private String email;
        private Instant subscribedAt;
    }


    // 1. POST /api/subscribe
    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be provided");
        }

        // Check if already subscribed
        boolean exists = subscribers.values().stream()
                .anyMatch(sub -> sub.getEmail().equalsIgnoreCase(request.getEmail()));

        if (exists) {
            log.info("Subscription attempt for already subscribed email: {}", request.getEmail());
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", null));
        }

        String id = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(id, request.getEmail(), Instant.now());
        subscribers.put(id, subscriber);

        log.info("New subscriber added: {}", subscriber.getEmail());

        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", id));
    }

    // 2. POST /api/facts/sendWeekly
    @PostMapping("/facts/sendWeekly")
    public ResponseEntity<SendWeeklyResponse> sendWeeklyCatFact() {
        String catFact;

        try {
            // Call external API https://catfact.ninja/fact
            String url = "https://catfact.ninja/fact";
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            catFact = rootNode.path("fact").asText();

            if (catFact.isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cat fact is empty");
            }
        } catch (Exception e) {
            log.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch cat fact");
        }

        lastFactSentAt = Instant.now();
        lastFactText = catFact;

        // Fire-and-forget sending emails
        fireAndForgetSendEmails(catFact);

        log.info("Weekly cat fact sent to {} subscribers", subscribers.size());

        return ResponseEntity.ok(new SendWeeklyResponse("Weekly cat fact sent", catFact));
    }

    @Async
    void fireAndForgetSendEmails(String catFact) {
        CompletableFuture.runAsync(() -> {
            // TODO: Replace this mock logic with actual email sending service integration
            try {
                for (Subscriber sub : subscribers.values()) {
                    log.info("Sending email to {} with fact: {}", sub.getEmail(), catFact);
                    // Simulate sending delay
                    Thread.sleep(50);
                }
                log.info("All emails sent successfully");
            } catch (InterruptedException e) {
                log.error("Email sending interrupted", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    // 3. GET /api/admin/stats
    @GetMapping("/admin/stats")
    public ResponseEntity<StatsResponse> getStats() {
        int totalSubs = subscribers.size();
        Instant lastSent = lastFactSentAt;
        return ResponseEntity.ok(new StatsResponse(totalSubs, lastSent));
    }

    // 4. POST /api/unsubscribe
    @PostMapping("/unsubscribe")
    public ResponseEntity<UnsubscribeResponse> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must be provided");
        }

        String emailLower = request.getEmail().toLowerCase();
        String keyToRemove = null;

        for (Map.Entry<String, Subscriber> entry : subscribers.entrySet()) {
            if (entry.getValue().getEmail().equalsIgnoreCase(emailLower)) {
                keyToRemove = entry.getKey();
                break;
            }
        }

        if (keyToRemove != null) {
            subscribers.remove(keyToRemove);
            log.info("Unsubscribed email: {}", request.getEmail());
            return ResponseEntity.ok(new UnsubscribeResponse("Unsubscribed successfully"));
        } else {
            log.info("Unsubscribe attempt for non-existent email: {}", request.getEmail());
            return ResponseEntity.ok(new UnsubscribeResponse("Email not found in subscription list"));
        }
    }

    // Minimal Exception handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }
}
```