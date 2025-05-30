package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
@Validated
@Slf4j
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<UUID, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, UUID> emailToId = new ConcurrentHashMap<>();
    private volatile CatFact latestCatFact;
    private final Map<String, Integer> interactionCounts = new ConcurrentHashMap<>();

    @PostMapping("/subscribers")
    public ResponseEntity<SubscriberResponse> registerSubscriber(@RequestBody @Valid SubscriberRequest request) {
        logger.info("Registering subscriber with email={}", request.getEmail());
        String emailKey = request.getEmail().toLowerCase(Locale.ROOT);
        if (emailToId.containsKey(emailKey)) {
            UUID existingId = emailToId.get(emailKey);
            Subscriber existing = subscribers.get(existingId);
            logger.info("Subscriber already exists with id={}", existingId);
            return ResponseEntity.ok(new SubscriberResponse(existing.getId(), existing.getEmail(), "subscribed"));
        }
        UUID newId = UUID.randomUUID();
        Subscriber sub = new Subscriber(newId, emailKey);
        subscribers.put(newId, sub);
        emailToId.put(emailKey, newId);
        logger.info("Subscriber registered with id={}", newId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubscriberResponse(newId, request.getEmail(), "subscribed"));
    }

    @GetMapping("/subscribers/count")
    public Map<String, Integer> getSubscriberCount() {
        int count = subscribers.size();
        logger.info("Returning subscriber count={}", count);
        return Collections.singletonMap("count", count);
    }

    @PostMapping("/catfact/fetch-and-send")
    public ResponseEntity<CatFactResponse> fetchAndSendCatFact() {
        logger.info("Triggered fetch and send cat fact");
        String url = "https://catfact.ninja/fact";
        JsonNode rootNode;
        try {
            String json = restTemplate.getForObject(url, String.class);
            rootNode = objectMapper.readTree(json);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }
        String factText = rootNode.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            logger.error("Cat fact missing in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cat fact missing in response");
        }
        UUID factId = UUID.randomUUID();
        Instant now = Instant.now();
        CatFact catFact = new CatFact(factId, factText, now);
        latestCatFact = catFact;
        int emailsSent = subscribers.size();
        CompletableFuture.runAsync(() -> sendEmails(catFact, subscribers.values()));
        logger.info("Cat fact fetched and email sending started to {} subscribers", emailsSent);
        return ResponseEntity.ok(new CatFactResponse(factId, factText, emailsSent));
    }

    @GetMapping("/catfact/latest")
    public ResponseEntity<CatFactResponseWithDate> getLatestCatFact() {
        if (latestCatFact == null) {
            logger.info("No cat fact sent yet");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new CatFactResponseWithDate(
                latestCatFact.getId(),
                latestCatFact.getFact(),
                latestCatFact.getSentDate().toString()));
    }

    @PostMapping("/interactions")
    public Map<String, String> recordInteraction(@RequestBody @Valid InteractionRequest request) {
        logger.info("Recording interaction: subscriberId={}, factId={}, type={}",
                request.getSubscriberId(), request.getFactId(), request.getInteractionType());
        if (!subscribers.containsKey(request.getSubscriberId())) {
            logger.error("Subscriber id not found: {}", request.getSubscriberId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        String type = request.getInteractionType().toLowerCase(Locale.ROOT);
        if (!("open".equals(type) || "click".equals(type))) {
            logger.error("Invalid interactionType: {}", request.getInteractionType());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid interactionType");
        }
        interactionCounts.merge(type, 1, Integer::sum);
        logger.info("Interaction recorded for type={}", type);
        return Collections.singletonMap("status", "recorded");
    }

    @GetMapping("/interactions/stats")
    public InteractionStatsResponse getInteractionStats() {
        int totalOpens = interactionCounts.getOrDefault("open", 0);
        int totalClicks = interactionCounts.getOrDefault("click", 0);
        logger.info("Returning interaction stats: opens={}, clicks={}", totalOpens, totalClicks);
        return new InteractionStatsResponse(totalOpens, totalClicks);
    }

    @Async
    public void sendEmails(CatFact catFact, Collection<Subscriber> subs) {
        logger.info("Simulating sending emails to {} subscribers", subs.size());
        try {
            Thread.sleep(1000);
            // TODO: Implement real email sending here
            logger.info("Emails sent with cat fact: {}", catFact.getFact());
        } catch (InterruptedException e) {
            logger.error("Email sending simulation interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SubscriberRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    private static class SubscriberResponse {
        private UUID subscriberId;
        private String email;
        private String status;
    }

    @Data
    @AllArgsConstructor
    private static class CatFactResponse {
        private UUID factId;
        private String catFact;
        private int emailsSent;
    }

    @Data
    @AllArgsConstructor
    private static class CatFactResponseWithDate {
        private UUID factId;
        private String catFact;
        private String sentDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class InteractionRequest {
        @NotNull
        private UUID subscriberId;
        @NotNull
        private UUID factId;
        @NotBlank
        @Pattern(regexp = "^(open|click)$")
        private String interactionType;
    }

    @Data
    @AllArgsConstructor
    private static class InteractionStatsResponse {
        private int totalOpens;
        private int totalClicks;
    }

    @Data
    @AllArgsConstructor
    private static class Subscriber {
        private UUID id;
        private String email;
    }

    @Data
    @AllArgsConstructor
    private static class CatFact {
        private UUID id;
        private String fact;
        private Instant sentDate;
    }
}