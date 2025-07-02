```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype/api")
public class EntityControllerPrototype {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, CatFact> factsHistory = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // For simplicity, we keep a simple counter for emails sent
    private int totalEmailsSent = 0;

    // === DTOs ===

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        private String email;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupResponse {
        private String userId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyFactSendResponse {
        private String factId;
        private String factText;
        private int recipientsCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private String userId;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatFact {
        private String factId;
        private String factText;
        private Instant sentAt;
        private int recipientsCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSummary {
        private int totalSubscribers;
        private int totalEmailsSent;
        private Instant lastFactSentAt;
    }

    // === Endpoint implementations ===

    /**
     * POST /api/users/signup
     * Registers a new subscriber.
     */
    @PostMapping(value = "/users/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signupUser(@Valid @RequestBody SignupRequest request) {
        log.info("Received signup request for email={}", request.getEmail());

        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }

        // Check for duplicate email
        boolean emailExists = subscribers.values().stream()
                .anyMatch(s -> s.getEmail().equalsIgnoreCase(request.getEmail()));
        if (emailExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }

        String userId = UUID.randomUUID().toString();
        Subscriber newSubscriber = new Subscriber(userId, request.getEmail(), request.getName(), Instant.now());
        subscribers.put(userId, newSubscriber);

        log.info("User subscribed successfully: userId={}, email={}", userId, request.getEmail());

        return ResponseEntity.created(URI.create("/prototype/api/users/" + userId))
                .body(new SignupResponse(userId, "Subscription successful."));
    }

    /**
     * POST /api/facts/sendWeekly
     * Retrieves a new cat fact from the Cat Fact API and sends it to all subscribers.
     */
    @PostMapping(value = "/facts/sendWeekly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeeklyFactSendResponse> sendWeeklyCatFact() {
        log.info("Triggering weekly cat fact retrieval and email sending");

        // Fetch cat fact from external API
        final String catFactApiUrl = "https://catfact.ninja/fact";
        JsonNode factJson;
        try {
            String rawJson = restTemplate.getForObject(catFactApiUrl, String.class);
            factJson = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            log.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to retrieve cat fact");
        }

        String factText = factJson.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            log.error("Cat fact API returned no fact text");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact API returned no fact");
        }

        String factId = UUID.randomUUID().toString();
        int recipientsCount = subscribers.size();

        CatFact catFact = new CatFact(factId, factText, Instant.now(), recipientsCount);
        factsHistory.put(factId, catFact);

        // Simulate sending emails asynchronously
        CompletableFuture.runAsync(() -> sendEmails(catFact));

        totalEmailsSent += recipientsCount;

        WeeklyFactSendResponse response = new WeeklyFactSendResponse(
                factId,
                factText,
                recipientsCount,
                "Weekly cat fact sent successfully."
        );

        log.info("Weekly cat fact sent: factId={}, recipientsCount={}", factId, recipientsCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Mocked email sending method.
     * TODO: Replace with real email sending implementation.
     */
    @Async
    void sendEmails(CatFact catFact) {
        log.info("Sending cat fact emails to {} subscribers ...", catFact.getRecipientsCount());

        // TODO: Implement real email sending logic here.
        // For prototype, we just simulate a delay:
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException ignored) {
        }

        log.info("Finished sending cat fact emails for factId={}", catFact.getFactId());
    }

    /**
     * GET /api/users
     * Retrieve all subscribers.
     */
    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        log.info("Retrieving all subscribers, count={}", subscribers.size());
        return ResponseEntity.ok(new ArrayList<>(subscribers.values()));
    }

    /**
     * GET /api/facts/history
     * Retrieve history of sent cat facts.
     */
    @GetMapping(value = "/facts/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CatFact>> getFactsHistory() {
        log.info("Retrieving cat fact history, count={}", factsHistory.size());
        // Sort by sentAt descending
        List<CatFact> sortedFacts = new ArrayList<>(factsHistory.values());
        sortedFacts.sort(Comparator.comparing(CatFact::getSentAt).reversed());
        return ResponseEntity.ok(sortedFacts);
    }

    /**
     * GET /api/report/summary
     * Provides summary metrics.
     */
    @GetMapping(value = "/report/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportSummary> getReportSummary() {
        log.info("Generating report summary");

        Instant lastSentAt = factsHistory.values().stream()
                .map(CatFact::getSentAt)
                .max(Instant::compareTo)
                .orElse(null);

        ReportSummary summary = new ReportSummary(
                subscribers.size(),
                totalEmailsSent,
                lastSentAt
        );

        return ResponseEntity.ok(summary);
    }

    // === Basic Exception Handling ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason(), ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}
```