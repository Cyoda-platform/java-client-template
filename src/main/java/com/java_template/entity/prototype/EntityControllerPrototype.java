package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Interaction> interactions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Invalid email format")
        private String email;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String id;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberResponse {
        private String subscriberId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendWeeklyResponse {
        private String status;
        private int sentCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberCountResponse {
        private int totalSubscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionSummary {
        private int emailOpens;
        private int linkClicks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionReportResponse {
        private InteractionSummary interactions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Interaction {
        private String subscriberId;
        private boolean emailOpened;
        private int linkClicks;
        private Instant lastInteractionAt;
    }

    @PostMapping("/subscribers")
    public ResponseEntity<SubscriberResponse> subscribe(@Valid @RequestBody SubscriberRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (subscribers.values().stream().anyMatch(s -> s.getEmail().equals(email))) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Email already subscribed");
        }
        String id = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(id, email, request.getName(), Instant.now());
        subscribers.put(id, subscriber);
        logger.info("New subscriber registered: {}", email);
        return ResponseEntity.ok(new SubscriberResponse(id, "Subscription successful"));
    }

    @PostMapping("/facts/send-weekly")
    public ResponseEntity<SendWeeklyResponse> sendWeeklyCatFact() {
        logger.info("Triggered weekly cat fact send-out");
        CompletableFuture.runAsync(() -> {
            try {
                JsonNode catFactJson = fetchCatFactFromExternalAPI();
                String fact = catFactJson.path("fact").asText(null);
                if (fact == null || fact.isEmpty()) {
                    logger.error("CatFact API returned empty fact");
                    return;
                }
                logger.info("Fetched cat fact: {}", fact);
                int sentCount = 0;
                for (Subscriber sub : subscribers.values()) {
                    sendEmailMock(sub, fact); // TODO: Replace with real email logic
                    sentCount++;
                }
                logger.info("Sent cat fact email to {} subscribers", sentCount);
            } catch (Exception e) {
                logger.error("Error during weekly cat fact send-out", e);
            }
        });
        return ResponseEntity.ok(new SendWeeklyResponse("success", subscribers.size()));
    }

    @GetMapping("/report/subscribers/count")
    public ResponseEntity<SubscriberCountResponse> getSubscriberCount() {
        int count = subscribers.size();
        logger.info("Subscriber count requested: {}", count);
        return ResponseEntity.ok(new SubscriberCountResponse(count));
    }

    @GetMapping("/report/interactions")
    public ResponseEntity<InteractionReportResponse> getInteractionReport() {
        int totalEmailOpens = (int) interactions.values().stream().filter(Interaction::isEmailOpened).count();
        int totalLinkClicks = interactions.values().stream().mapToInt(Interaction::getLinkClicks).sum();
        logger.info("Interaction report requested: opens={}, clicks={}", totalEmailOpens, totalLinkClicks);
        InteractionSummary summary = new InteractionSummary(totalEmailOpens, totalLinkClicks);
        return ResponseEntity.ok(new InteractionReportResponse(summary));
    }

    private JsonNode fetchCatFactFromExternalAPI() {
        try {
            String jsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (jsonStr == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty response from CatFact API");
            }
            return objectMapper.readTree(jsonStr);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }
    }

    private void sendEmailMock(Subscriber subscriber, String fact) {
        // TODO: Replace this mock with real email sending logic
        logger.info("Sending cat fact email to {} <{}>: Fact: {}", subscriber.getName(), subscriber.getEmail(), fact);
        interactions.compute(subscriber.getId(), (id, existing) -> {
            if (existing == null) {
                return new Interaction(id, false, 0, Instant.now());
            }
            return existing;
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}