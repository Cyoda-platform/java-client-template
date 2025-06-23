package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<UUID, CatFact> facts = new ConcurrentHashMap<>();
    private final Map<UUID, FactInteraction> interactions = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscriber> addSubscriber(@RequestBody @Valid SubscriberRequest request) {
        Optional<Subscriber> existing = subscribers.values().stream()
                .filter(s -> s.getEmail().equalsIgnoreCase(request.getEmail()))
                .findFirst();
        if (existing.isPresent()) {
            log.info("Subscriber with email {} already exists: {}", request.getEmail(), existing.get().getSubscriberId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(existing.get());
        }
        Subscriber subscriber = new Subscriber(UUID.randomUUID(), request.getEmail(), "SUBSCRIBED", Instant.now());
        subscribers.put(subscriber.getSubscriberId(), subscriber);
        log.info("New subscriber added: {}", subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping(value = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscriber>> listSubscribers() {
        return ResponseEntity.ok(new ArrayList<>(subscribers.values()));
    }

    @PostMapping(value = "/facts/sendWeekly", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FactSentResponse> sendWeeklyCatFact(@RequestBody @Valid FactSendRequest request) {
        log.info("Received request to send weekly cat fact, triggeredBy={}", request.getTriggeredBy());
        JsonNode jsonNode;
        try {
            String response = restTemplate.getForObject(URI.create(CAT_FACT_API_URL), String.class);
            jsonNode = objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }
        String factText = jsonNode.hasNonNull("fact") ? jsonNode.get("fact").asText() : null;
        if (factText == null || factText.isBlank()) {
            log.error("Cat fact text missing or empty in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid cat fact received");
        }
        UUID factId = UUID.randomUUID();
        CatFact fact = new CatFact(factId, factText, Instant.now());
        facts.put(factId, fact);
        log.info("New cat fact stored: {}", fact);
        sendEmailsToSubscribersAsync(fact);
        FactSentResponse response = new FactSentResponse(factId, factText, subscribers.size());
        return ResponseEntity.ok(response);
    }

    @Async
    void sendEmailsToSubscribersAsync(CatFact fact) {
        CompletableFuture.runAsync(() -> {
            log.info("Starting async email sending to {} subscribers", subscribers.size());
            subscribers.values().forEach(s -> {
                log.info("Sent cat fact email to subscriber: {}", s.getEmail());
                interactions.merge(fact.getFactId(),
                        new FactInteraction(fact.getFactId(), 1, 0, 0),
                        (oldVal, newVal) -> {
                            oldVal.setEmailsSent(oldVal.getEmailsSent() + 1);
                            return oldVal;
                        });
            });
            log.info("Finished sending cat fact emails");
        });
    }

    @GetMapping(value = "/reports/subscribersCount", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubscribersCountResponse> getSubscribersCount() {
        SubscribersCountResponse response = new SubscribersCountResponse(subscribers.size());
        log.info("Reporting total subscribers: {}", response.getTotalSubscribers());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/reports/factInteractions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FactInteraction>> getFactInteractions() {
        List<FactInteraction> list = new ArrayList<>(interactions.values());
        log.info("Reporting fact interactions count: {}", list.size());
        return ResponseEntity.ok(list);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Request failed: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @Data
    public static class SubscriberRequest {
        @NotBlank(message = "Email must be provided")
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    @AllArgsConstructor
    public static class Subscriber {
        private UUID subscriberId;
        private String email;
        private String status;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    public static class CatFact {
        private UUID factId;
        private String factText;
        private Instant createdAt;
    }

    @Data
    public static class FactSendRequest {
        @NotBlank(message = "triggeredBy must be provided")
        private String triggeredBy;
    }

    @Data
    @AllArgsConstructor
    public static class FactSentResponse {
        private UUID factId;
        private String factText;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SubscribersCountResponse {
        private int totalSubscribers;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FactInteraction {
        private UUID factId;
        private int emailsSent;
        private int emailsOpened;
        private int linksClicked;
    }
}