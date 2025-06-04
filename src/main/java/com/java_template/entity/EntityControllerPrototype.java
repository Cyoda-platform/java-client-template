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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
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
@Validated
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

    // POST endpoint must be first annotation
    @PostMapping("/subscribers")
    public ResponseEntity<Subscriber> createSubscriber(@RequestBody @Valid SubscriberRequest request) {
        log.info("Received subscriber creation request for email: {}", request.getEmail());

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

        // TODO: Replace with real async email sending logic
        CompletableFuture.runAsync(() -> sendCatFactEmails(catFactText));

        FactSendResponse response = new FactSendResponse(subscriberCount, catFactText);
        log.info("Cat fact sent to {} subscribers", subscriberCount);
        return ResponseEntity.ok(response);
    }

    @Async
    void sendCatFactEmails(String catFactText) {
        log.info("Sending cat fact emails to {} subscribers...", subscribers.size());
        subscribers.values().forEach(subscriber -> {
            log.info("Sending email to {}: {}", subscriber.getEmail(), catFactText);
        });
    }

    @GetMapping("/report/subscribers")
    public ResponseEntity<SubscriberReport> getSubscriberReport() {
        int total = subscribers.size();
        log.info("Subscriber report requested, total: {}", total);
        return ResponseEntity.ok(new SubscriberReport(total));
    }

    @GetMapping("/report/interaction")
    public ResponseEntity<InteractionReport> getInteractionReport() {
        int emailOpens = interactionReports.values().stream().mapToInt(InteractionReport::getEmailOpens).sum();
        int linkClicks = interactionReports.values().stream().mapToInt(InteractionReport::getLinkClicks).sum();

        InteractionReport report = new InteractionReport(emailOpens, linkClicks);
        log.info("Interaction report requested: opens={}, clicks={}", emailOpens, linkClicks);
        return ResponseEntity.ok(report);
    }

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        @NotBlank
        @Email
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