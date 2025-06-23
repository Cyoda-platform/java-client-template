```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@Validated
@RequestMapping("/api/cyoda")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_CATFACT = "CatFact";
    private static final String ENTITY_NAME_FACTINTERACTION = "FactInteraction";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Workflow function for Subscriber entity
    private Function<Object, Object> processSubscriber = entity -> {
        if (entity instanceof Subscriber) {
            Subscriber subscriber = (Subscriber) entity;
            // Example: ensure status is uppercase
            if (subscriber.getStatus() != null) {
                subscriber.setStatus(subscriber.getStatus().toUpperCase());
            }
            // You can add more processing logic here
            return subscriber;
        }
        return entity;
    };

    // Workflow function for CatFact entity
    private Function<Object, Object> processCatFact = entity -> {
        if (entity instanceof CatFact) {
            CatFact catFact = (CatFact) entity;
            // Example: add createdAt if null
            if (catFact.getCreatedAt() == null) {
                catFact.setCreatedAt(Instant.now());
            }
            // Additional processing logic can be added here
            return catFact;
        }
        return entity;
    };

    // Workflow function for FactInteraction entity (if needed)
    private Function<Object, Object> processFactInteraction = entity -> {
        // No specific processing now, just return as is
        return entity;
    };

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscriber> addSubscriber(@RequestBody @Valid SubscriberRequest request) {
        // Fetch all subscribers to check for existing email
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subscribersArray = subscribersFuture.join();

        for (JsonNode node : subscribersArray) {
            String email = node.get("email").asText();
            if (email.equalsIgnoreCase(request.getEmail())) {
                UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                Subscriber existingSubscriber = new Subscriber(technicalId, email, node.get("status").asText(),
                        Instant.parse(node.get("subscribedAt").asText()));
                logger.info("Subscriber with email {} already exists: {}", request.getEmail(), technicalId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(existingSubscriber);
            }
        }

        Subscriber subscriber = new Subscriber(null, request.getEmail(), "SUBSCRIBED", Instant.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, processSubscriber);
        UUID technicalId = idFuture.join();
        subscriber.setSubscriberId(technicalId);
        logger.info("New subscriber added: {}", subscriber);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriber);
    }

    @GetMapping(value = "/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Subscriber>> listSubscribers() {
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subscribersArray = subscribersFuture.join();
        List<Subscriber> subscribersList = new ArrayList<>();
        for (JsonNode node : subscribersArray) {
            UUID technicalId = UUID.fromString(node.get("technicalId").asText());
            String email = node.get("email").asText();
            String status = node.get("status").asText();
            Instant subscribedAt = Instant.parse(node.get("subscribedAt").asText());
            subscribersList.add(new Subscriber(technicalId, email, status, subscribedAt));
        }
        return ResponseEntity.ok(subscribersList);
    }

    @PostMapping(value = "/facts/sendWeekly", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FactSentResponse> sendWeeklyCatFact(@RequestBody @Valid FactSendRequest request) {
        logger.info("Received request to send weekly cat fact, triggeredBy={}", request.getTriggeredBy());
        JsonNode jsonNode;
        try {
            String response = restTemplate.getForObject(URI.create(CAT_FACT_API_URL), String.class);
            jsonNode = objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }
        String factText = jsonNode.hasNonNull("fact") ? jsonNode.get("fact").asText() : null;
        if (factText == null || factText.isBlank()) {
            logger.error("Cat fact text missing or empty in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid cat fact received");
        }
        CatFact fact = new CatFact(null, factText, Instant.now());
        CompletableFuture<UUID> factIdFuture = entityService.addItem(ENTITY_NAME_CATFACT, ENTITY_VERSION, fact, processCatFact);
        UUID factId = factIdFuture.join();
        fact.setFactId(factId);
        logger.info("New cat fact stored: {}", fact);
        sendEmailsToSubscribersAsync(fact);
        // Fetch subscribers count
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        int subscribersCount = subscribersFuture.join().size();
        FactSentResponse response = new FactSentResponse(factId, factText, subscribersCount);
        return ResponseEntity.ok(response);
    }

    @Async
    void sendEmailsToSubscribersAsync(CatFact fact) {
        CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
                ArrayNode subscribers = subscribersFuture.join();
                logger.info("Starting async email sending to {} subscribers", subscribers.size());
                for (JsonNode s : subscribers) {
                    String email = s.get("email").asText();
                    logger.info("Sent cat fact email to subscriber: {}", email);
                }
                // Update interactions
                CompletableFuture<ArrayNode> interactionsFuture = entityService.getItemsByCondition(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION,
                        objectMapper.createObjectNode().put("factId", fact.getFactId().toString()));
                ArrayNode existingInteractions = interactionsFuture.join();

                if (existingInteractions.isEmpty()) {
                    FactInteraction newInteraction = new FactInteraction(fact.getFactId(), 1, 0, 0);
                    entityService.addItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, newInteraction, processFactInteraction).join();
                } else {
                    for (JsonNode interactionNode : existingInteractions) {
                        UUID technicalId = UUID.fromString(interactionNode.get("technicalId").asText());
                        int emailsSent = interactionNode.get("emailsSent").asInt() + 1;
                        FactInteraction updatedInteraction = new FactInteraction(fact.getFactId(), emailsSent,
                                interactionNode.get("emailsOpened").asInt(), interactionNode.get("linksClicked").asInt());
                        entityService.updateItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, technicalId, updatedInteraction).join();
                    }
                }
                logger.info("Finished sending cat fact emails");
            } catch (Exception e) {
                logger.error("Error during async email sending", e);
            }
        });
    }

    @GetMapping(value = "/reports/subscribersCount", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SubscribersCountResponse> getSubscribersCount() {
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        int count = subscribersFuture.join().size();
        SubscribersCountResponse response = new SubscribersCountResponse(count);
        logger.info("Reporting total subscribers: {}", count);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/reports/factInteractions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FactInteraction>> getFactInteractions() {
        CompletableFuture<ArrayNode> interactionsFuture = entityService.getItems(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION);
        ArrayNode interactionsArray = interactionsFuture.join();
        List<FactInteraction> list = new ArrayList<>();
        for (JsonNode node : interactionsArray) {
            UUID technicalId = UUID.fromString(node.get("technicalId").asText());
            UUID factId = UUID.fromString(node.get("factId").asText());
            int emailsSent = node.get("emailsSent").asInt();
            int emailsOpened = node.get("emailsOpened").asInt();
            int linksClicked = node.get("linksClicked").asInt();
            list.add(new FactInteraction(factId, emailsSent, emailsOpened, linksClicked));
        }
        logger.info("Reporting fact interactions count: {}", list.size());
        return ResponseEntity.ok(list);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Request failed: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);
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
    @NoArgsConstructor
    public static class Subscriber {
        private UUID subscriberId;
        private String email;
        private String status;
        private Instant subscribedAt;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
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
    @NoArgsConstructor
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
```
---

**Explanation of changes:**

- Added private `Function<Object, Object>` workflow functions named `processSubscriber`, `processCatFact`, and `processFactInteraction` that correspond to the entity names and follow the naming convention `process{EntityName}`.

- Each workflow function accepts the entity, processes or modifies it as needed, and returns it.

- Updated all calls to `entityService.addItem` to pass the new workflow function as the last argument.

- For example, 
  ```java
  entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber, processSubscriber);
  ```

- This meets the requirement that the workflow function is applied asynchronously before persistence, and prevents infinite recursion because the function does not add/update/delete the same entity model.

---

If you want me to update other usages like `updateItem` to include workflow functions or add more detailed processing inside those functions, just let me know!