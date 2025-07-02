package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/api")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, CatFact> factsHistory = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private int totalEmailsSent = 0;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignupRequest {
        @NotBlank
        @Email
        private String email;
        @Size(max = 100)
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

    @PostMapping(value = "/users/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signupUser(@Valid @RequestBody SignupRequest request) {
        logger.info("Signup request received for email={}", request.getEmail());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }
        boolean emailExists = subscribers.values().stream()
            .anyMatch(s -> s.getEmail().equalsIgnoreCase(request.getEmail()));
        if (emailExists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }
        String userId = UUID.randomUUID().toString();
        Subscriber newSubscriber = new Subscriber(userId, request.getEmail(), request.getName(), Instant.now());
        subscribers.put(userId, newSubscriber);
        logger.info("User subscribed: userId={}, email={}", userId, request.getEmail());
        return ResponseEntity.created(URI.create("/prototype/api/users/" + userId))
            .body(new SignupResponse(userId, "Subscription successful."));
    }

    @PostMapping(value = "/facts/sendWeekly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WeeklyFactSendResponse> sendWeeklyCatFact() {
        logger.info("Triggering weekly cat fact retrieval");
        String catFactApiUrl = "https://catfact.ninja/fact";
        JsonNode factJson;
        try {
            String rawJson = restTemplate.getForObject(catFactApiUrl, String.class);
            factJson = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to retrieve cat fact");
        }
        String factText = factJson.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            logger.error("No fact text in API response");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cat fact API returned no fact");
        }
        String factId = UUID.randomUUID().toString();
        int recipientsCount = subscribers.size();
        CatFact catFact = new CatFact(factId, factText, Instant.now(), recipientsCount);
        factsHistory.put(factId, catFact);
        CompletableFuture.runAsync(() -> sendEmails(catFact)); // fire-and-forget
        totalEmailsSent += recipientsCount;
        logger.info("Weekly cat fact sent: factId={}, recipientsCount={}", factId, recipientsCount);
        return ResponseEntity.ok(new WeeklyFactSendResponse(factId, factText, recipientsCount, "Weekly cat fact sent successfully."));
    }

    @Async
    void sendEmails(CatFact catFact) {
        logger.info("Sending emails to {} subscribers", catFact.getRecipientsCount());
        // TODO: Replace with real email sending implementation
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException ignored) {
        }
        logger.info("Email sending completed for factId={}", catFact.getFactId());
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        logger.info("Retrieving subscribers, count={}", subscribers.size());
        return ResponseEntity.ok(new ArrayList<>(subscribers.values()));
    }

    @GetMapping(value = "/facts/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CatFact>> getFactsHistory() {
        logger.info("Retrieving fact history, count={}", factsHistory.size());
        List<CatFact> sortedFacts = new ArrayList<>(factsHistory.values());
        sortedFacts.sort(Comparator.comparing(CatFact::getSentAt).reversed());
        return ResponseEntity.ok(sortedFacts);
    }

    @GetMapping(value = "/report/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportSummary> getReportSummary() {
        logger.info("Generating report summary");
        Instant lastSentAt = factsHistory.values().stream()
            .map(CatFact::getSentAt)
            .max(Instant::compareTo)
            .orElse(null);
        return ResponseEntity.ok(new ReportSummary(subscribers.size(), totalEmailsSent, lastSentAt));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason(), ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
    }
}