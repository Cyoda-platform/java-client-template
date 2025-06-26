package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class Controller {

    private static final String NBA_API_KEY = "test"; // TODO: secure config
    private static final String NBA_API_URL_TEMPLATE = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/%s?key=%s";

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    // In-memory subscribers cache, thread-safe map to avoid concurrency issues
    private final Map<String, Subscriber> subscribers = Collections.synchronizedMap(new HashMap<>());

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SubscribeRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class SubscribeResponse {
        private String message;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private OffsetDateTime subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FetchScoresRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String date;
    }

    @Data
    @AllArgsConstructor
    static class FetchScoresResponse {
        private String message;
    }

    @PostConstruct
    void initDemo() {
        // Initialize demo subscriber safely
        subscribers.putIfAbsent("demo@example.com", new Subscriber("demo@example.com", OffsetDateTime.now()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        log.info("Subscription request for {}", email);
        synchronized (subscribers) {
            if (subscribers.containsKey(email)) {
                return ResponseEntity.ok(new SubscribeResponse("Email already subscribed", email));
            }
            subscribers.put(email, new Subscriber(email, OffsetDateTime.now()));
        }
        return ResponseEntity.ok(new SubscribeResponse("Subscription successful", email));
    }

    @DeleteMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> deleteSubscription(@RequestParam @NotBlank @Email String email) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        log.info("Unsubscribe request for email: {}", normalizedEmail);
        Map<String, String> response = new HashMap<>();
        synchronized (subscribers) {
            if (subscribers.remove(normalizedEmail) != null) {
                response.put("message", "Unsubscribed successfully");
                log.info("Email {} unsubscribed", normalizedEmail);
                return ResponseEntity.ok(response);
            }
        }
        response.put("message", "Email not found in subscribers");
        log.info("Email {} not found for unsubscribe", normalizedEmail);
        return ResponseEntity.status(404).body(response);
    }

    @PostMapping("/scores/fetch")
    public CompletableFuture<ResponseEntity<FetchScoresResponse>> fetchScores(@RequestBody @Valid FetchScoresRequest request) {
        try {
            LocalDate.parse(request.getDate());
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid date format, expected YYYY-MM-DD");
        }
        ObjectNode entityData = objectMapper.convertValue(request, ObjectNode.class);
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CyodaPrototype",
                ENTITY_VERSION,
                entityData
        );
        return idFuture.thenApply(id -> ResponseEntity.ok(new FetchScoresResponse("Scores fetching started, entity id: " + id)))
                .exceptionally(ex -> {
                    log.error("Failed to add fetch scores entity", ex);
                    return ResponseEntity.status(500).body(new FetchScoresResponse("Failed to start fetching: " + ex.getMessage()));
                });
    }

    /**
     * Fire-and-forget method to send email notifications to subscribers.
     */
    private void sendEmailNotifications(String date) {
        try {
            List<String> emails;
            synchronized (subscribers) {
                emails = new ArrayList<>(subscribers.keySet());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("NBA Scores updated for date ").append(date).append(".\n");
            // In a real app, fetch game details from DB or cache; here just notify subscribers
            for (String email : emails) {
                log.info("Sending email to {}: {}", email, sb.toString());
                // Implement actual email sending here if needed
            }
        } catch (Exception e) {
            log.error("Failed to send email notifications", e);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        log.error("Status exception: {}", ex.toString());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        log.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(err, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}