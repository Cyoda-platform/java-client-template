package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@RequestBody @Valid Subscriber subscriber) {
        logger.info("Received sign-up request for email: {}", subscriber.getEmail());
        String userId = String.valueOf(subscriber.getEmail().hashCode());
        subscribers.put(userId, subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "User successfully signed up", "userId", userId));
    }

    @PostMapping("/catfact")
    public ResponseEntity<?> getCatFact() {
        logger.info("Fetching a new cat fact");
        String apiUrl = "https://catfact.ninja/fact";
        try {
            String response = restTemplate.getForObject(apiUrl, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            String fact = jsonNode.get("fact").asText();
            return ResponseEntity.ok(Map.of("fact", fact));
        } catch (Exception e) {
            logger.error("Error fetching cat fact: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve cat fact");
        }
    }

    @PostMapping("/sendWeeklyEmail")
    public ResponseEntity<?> sendWeeklyEmail() {
        logger.info("Initiating weekly email send-out");

        CompletableFuture.runAsync(() -> {
            subscribers.forEach((id, subscriber) -> {
                // TODO: Implement actual email sending logic here
                logger.info("Sending email to {}", subscriber.getEmail());
            });
        });

        return ResponseEntity.ok(Map.of("message", "Weekly emails sent to all subscribers"));
    }

    @GetMapping("/stats/subscribers")
    public ResponseEntity<?> getSubscriberStats() {
        logger.info("Retrieving subscriber stats");

        // Mock interaction data
        int totalSubscribers = subscribers.size();
        int opens = (int) (totalSubscribers * 0.8); // Mock 80% open rate
        int clicks = (int) (totalSubscribers * 0.6); // Mock 60% click rate

        return ResponseEntity.ok(Map.of(
                "totalSubscribers", totalSubscribers,
                "weeklyInteractions", Map.of("opens", opens, "clicks", clicks)
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getStatusCode().toString()));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Subscriber {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 1, max = 100)
        private String name;
    }
}