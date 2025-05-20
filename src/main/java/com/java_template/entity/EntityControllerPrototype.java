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
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Instant> unsubscribes = new ConcurrentHashMap<>();
    private final Map<String, Integer> emailOpens = new ConcurrentHashMap<>();
    private final Map<String, CatFact> catFacts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Subscriber subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (subscribers.containsKey(email)) {
            log.info("Subscribe attempt for existing email: {}", email);
            return subscribers.get(email);
        }
        Subscriber subscriber = new Subscriber(email, Instant.now());
        subscribers.put(email, subscriber);
        log.info("New subscriber added: {}", email);
        return subscriber;
    }

    @PostMapping(value = "/subscribers/unsubscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> unsubscribe(@RequestBody @Valid UnsubscribeRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (!subscribers.containsKey(email)) {
            log.warn("Unsubscribe attempt for non-existing email: {}", email);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        subscribers.remove(email);
        unsubscribes.put(email, Instant.now());
        log.info("Unsubscribed: {}", email);
        return Map.of("message", "Unsubscribed successfully");
    }

    @PostMapping(value = "/catfact/send", produces = MediaType.APPLICATION_JSON_VALUE)
    public CatFactResponse sendWeeklyCatFact() {
        log.info("Starting weekly cat fact fetch and send");
        JsonNode root;
        try {
            var response = restTemplate.getForEntity(new URI(CAT_FACT_API_URL), String.class);
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
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact missing");
        }
        String factId = String.valueOf(Instant.now().toEpochMilli());
        CatFact catFact = new CatFact(factId, factText, Instant.now());
        catFacts.put(factId, catFact);
        log.info("Stored new cat fact: {}", catFact);
        CompletableFuture.runAsync(() -> sendEmailsToSubscribers(catFact));
        return new CatFactResponse(factId, factText, catFact.getTimestamp(), subscribers.size());
    }

    @Async
    void sendEmailsToSubscribers(CatFact catFact) {
        subscribers.keySet().forEach(email -> log.info("Sending cat fact email to {}", email)); // TODO: replace with real email logic
    }

    @GetMapping(value = "/reporting/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportingSummary getReportingSummary() {
        int totalSubscribers = subscribers.size();
        int totalEmailOpens = emailOpens.values().stream().mapToInt(Integer::intValue).sum();
        int totalUnsubscribes = unsubscribes.size();
        log.info("Reporting summary: subscribers={}, opens={}, unsubscribes={}", totalSubscribers, totalEmailOpens, totalUnsubscribes);
        return new ReportingSummary(totalSubscribers, totalEmailOpens, totalUnsubscribes);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getReason());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private Instant subscribedAt;
    }

    @Data
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    static class UnsubscribeRequest {
        @NotBlank
        @Email
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