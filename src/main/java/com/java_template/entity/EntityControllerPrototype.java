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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@Validated
@Slf4j
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private volatile Instant lastFactSentAt = null;
    private volatile String lastFactText = null;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @NotBlank
        @Email
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
        @NotBlank
        @Email
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

    @PostMapping("/subscribe") // must be first
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail();
        boolean exists = subscribers.values().stream()
                .anyMatch(sub -> sub.getEmail().equalsIgnoreCase(email));
        if (exists) {
            log.info("Subscription attempt for already subscribed email: {}", email);
            return ResponseEntity.ok(new SubscribeResponse("Already subscribed", null));
        }
        String id = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(id, email, Instant.now());
        subscribers.put(id, subscriber);
        log.info("New subscriber added: {}", email);
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", id));
    }

    @PostMapping("/facts/sendWeekly")
    public ResponseEntity<SendWeeklyResponse> sendWeeklyCatFact() {
        String catFact;
        try {
            String url = "https://catfact.ninja/fact";
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            catFact = rootNode.path("fact").asText();
            if (catFact.isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cat fact is empty");
            }
        } catch (Exception e) {
            log.error("Failed to fetch cat fact", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch cat fact");
        }
        lastFactSentAt = Instant.now();
        lastFactText = catFact;
        fireAndForgetSendEmails(catFact); // TODO: integrate real email service
        log.info("Weekly cat fact sent to {} subscribers", subscribers.size());
        return ResponseEntity.ok(new SendWeeklyResponse("Weekly cat fact sent", catFact));
    }

    @Async
    void fireAndForgetSendEmails(String catFact) {
        CompletableFuture.runAsync(() -> {
            for (Subscriber sub : subscribers.values()) {
                log.info("Sending email to {} with fact: {}", sub.getEmail(), catFact);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
            log.info("All emails sent");
        });
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(new StatsResponse(subscribers.size(), lastFactSentAt));
    }

    @PostMapping("/unsubscribe") // must be first
    public ResponseEntity<UnsubscribeResponse> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        String emailLower = request.getEmail().toLowerCase();
        String keyToRemove = subscribers.entrySet().stream()
                .filter(entry -> entry.getValue().getEmail().equalsIgnoreCase(emailLower))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (keyToRemove != null) {
            subscribers.remove(keyToRemove);
            log.info("Unsubscribed email: {}", request.getEmail());
            return ResponseEntity.ok(new UnsubscribeResponse("Unsubscribed successfully"));
        } else {
            log.info("Unsubscribe attempt for non-existent email: {}", request.getEmail());
            return ResponseEntity.ok(new UnsubscribeResponse("Email not found in subscription list"));
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }
}