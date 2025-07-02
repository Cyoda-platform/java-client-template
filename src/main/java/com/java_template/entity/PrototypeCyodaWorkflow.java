```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
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
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

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

    // Use ENTITY_VERSION constant as required
    private static final String ENTITY_MODEL_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_MODEL_CATFACT = "CatFact";

    private int totalEmailsSent = 0;

    /**
     * Workflow function for Subscriber entity.
     * This function is applied to Subscriber entities before persistence.
     * Here it returns the entity as is but you can modify entity state or add related entities.
     */
    private Function<Object, Object> processSubscriber = entity -> {
        // Cast to Subscriber to work with fields if needed
        Subscriber subscriber = (Subscriber) entity;
        // Example: you could modify subscriber here if needed
        // For now, return as is
        return subscriber;
    };

    /**
     * Workflow function for CatFact entity.
     * This function is applied to CatFact entities before persistence.
     * Here it returns the entity as is but you can modify entity state or add related entities.
     */
    private Function<Object, Object> processCatFact = entity -> {
        CatFact catFact = (CatFact) entity;
        // Example: modify catFact or add related entities if needed
        return catFact;
    };

    @PostMapping(value = "/users/signup", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signupUser(@Valid @RequestBody SignupRequest request) {
        logger.info("Signup request received for email={}", request.getEmail());
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be empty");
        }

        // Build condition to check if email exists ignoring case
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", request.getEmail()));

        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> existingSubsFuture =
                entityService.getItemsByCondition(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION, condition);

        com.fasterxml.jackson.databind.node.ArrayNode existingSubs = existingSubsFuture.join();

        if (existingSubs != null && existingSubs.size() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }

        String userId = UUID.randomUUID().toString();
        Subscriber newSubscriber = new Subscriber(userId, request.getEmail(), request.getName(), Instant.now());

        // Add workflow function processSubscriber as new parameter
        CompletableFuture<java.util.UUID> addFuture = entityService.addItem(
                ENTITY_MODEL_SUBSCRIBER,
                ENTITY_VERSION,
                newSubscriber,
                processSubscriber
        );
        addFuture.join(); // wait for completion

        logger.info("User subscribed: userId={}, email={}", userId, request.getEmail());
        return ResponseEntity.created(URI.create("/cyoda/api/users/" + userId))
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

        // Get all subscribers count from entity service
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> subsFuture =
                entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode subsArray = subsFuture.join();

        int recipientsCount = subsArray != null ? subsArray.size() : 0;
        String factId = UUID.randomUUID().toString();
        CatFact catFact = new CatFact(factId, factText, Instant.now(), recipientsCount);

        // Add workflow function processCatFact as new parameter
        CompletableFuture<java.util.UUID> addCatFactFuture = entityService.addItem(
                ENTITY_MODEL_CATFACT,
                ENTITY_VERSION,
                catFact,
                processCatFact
        );
        addCatFactFuture.join();

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
        logger.info("Retrieving subscribers");
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> subsFuture =
                entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode subsArray = subsFuture.join();

        List<Subscriber> subscribers = new ArrayList<>();
        if (subsArray != null) {
            subsArray.forEach(node -> {
                try {
                    Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                    subscribers.add(s);
                } catch (Exception e) {
                    logger.error("Failed to parse subscriber entity", e);
                }
            });
        }
        logger.info("Retrieved subscribers count={}", subscribers.size());
        return ResponseEntity.ok(subscribers);
    }

    @GetMapping(value = "/facts/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CatFact>> getFactsHistory() {
        logger.info("Retrieving fact history");
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> factsFuture =
                entityService.getItems(ENTITY_MODEL_CATFACT, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode factsArray = factsFuture.join();

        List<CatFact> catFacts = new ArrayList<>();
        if (factsArray != null) {
            factsArray.forEach(node -> {
                try {
                    CatFact cf = objectMapper.treeToValue(node, CatFact.class);
                    catFacts.add(cf);
                } catch (Exception e) {
                    logger.error("Failed to parse cat fact entity", e);
                }
            });
        }

        catFacts.sort(Comparator.comparing(CatFact::getSentAt).reversed());
        logger.info("Retrieved fact history count={}", catFacts.size());
        return ResponseEntity.ok(catFacts);
    }

    @GetMapping(value = "/report/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReportSummary> getReportSummary() {
        logger.info("Generating report summary");
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> factsFuture =
                entityService.getItems(ENTITY_MODEL_CATFACT, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode factsArray = factsFuture.join();

        Instant lastSentAt = null;
        if (factsArray != null && factsArray.size() > 0) {
            List<CatFact> catFacts = new ArrayList<>();
            factsArray.forEach(node -> {
                try {
                    CatFact cf = objectMapper.treeToValue(node, CatFact.class);
                    catFacts.add(cf);
                } catch (Exception e) {
                    logger.error("Failed to parse cat fact entity", e);
                }
            });
            lastSentAt = catFacts.stream()
                    .map(CatFact::getSentAt)
                    .max(Instant::compareTo)
                    .orElse(null);
        }

        // Get total subscribers count
        CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> subsFuture =
                entityService.getItems(ENTITY_MODEL_SUBSCRIBER, ENTITY_VERSION);
        com.fasterxml.jackson.databind.node.ArrayNode subsArray = subsFuture.join();
        int totalSubscribers = subsArray != null ? subsArray.size() : 0;

        return ResponseEntity.ok(new ReportSummary(totalSubscribers, totalEmailsSent, lastSentAt));
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
```
---

### Explanation of Changes:

- Added two workflow functions `processSubscriber` and `processCatFact` as private `Function<Object, Object>` fields.
- Each workflow function accepts an entity object, potentially modifies it, and returns it. Currently, they return the entity unchanged.
- Updated `entityService.addItem(...)` calls in `signupUser` and `sendWeeklyCatFact` methods to include the workflow function as the last argument.
- The new signature usage is:  
  ```java
  entityService.addItem(entityModel, entityVersion, entity, workflowFunction);
  ```
  
This satisfies the requirement that the workflow function with prefix 'process{EntityName}' is passed and applied before persistence.