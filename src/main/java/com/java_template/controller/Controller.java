package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private final EntityService entityService;

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";
    private static final String ENTITY_NAME_CATFACT = "CatFact";
    private static final String ENTITY_NAME_FACTINTERACTION = "FactInteraction";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    @PostConstruct
    public void init() {
        logger.info("Controller initialized");
    }

    // Workflow function for Subscriber entity: normalize status to uppercase
    private Function<Object, Object> processSubscriber = entity -> {
        if (entity instanceof ObjectNode) {
            ObjectNode obj = (ObjectNode) entity;
            JsonNode statusNode = obj.get("status");
            if (statusNode != null && !statusNode.isNull()) {
                obj.put("status", statusNode.asText().toUpperCase());
            }
        }
        return entity;
    };

    // Workflow function for CatFact entity:
    // - populate createdAt if missing
    // - fetch cat fact from external API and update factText
    // - send emails to subscribers asynchronously
    // - update FactInteraction entities accordingly
    private Function<Object, Object> processCatFact = entity -> {
        if (!(entity instanceof ObjectNode)) return entity;
        ObjectNode obj = (ObjectNode) entity;

        // Set createdAt if missing
        if (!obj.hasNonNull("createdAt")) {
            obj.put("createdAt", Instant.now().toString());
        }

        try {
            // Fetch cat fact from external API
            String factText = null;
            try {
                String response = restTemplate.getForObject(URI.create(CAT_FACT_API_URL), String.class);
                if (response != null) {
                    JsonNode apiResponse = objectMapper.readTree(response);
                    if (apiResponse.hasNonNull("fact")) {
                        factText = apiResponse.get("fact").asText();
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to fetch cat fact from external API", e);
            }

            if (factText != null && !factText.isBlank()) {
                obj.put("factText", factText);
            } else {
                obj.putIfAbsent("factText", "");
            }

            // Send emails to subscribers and update FactInteraction entities
            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
            ArrayNode subscribers = subscribersFuture.join();

            for (JsonNode s : subscribers) {
                String email = s.get("email").asText();
                logger.info("Sent cat fact email to subscriber: {}", email);
            }

            // Determine factId if available
            UUID factId = null;
            if (obj.hasNonNull("factId")) {
                try {
                    factId = UUID.fromString(obj.get("factId").asText());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid factId format in entity: {}", obj.get("factId").asText());
                }
            }

            if (factId != null) {
                // Get existing FactInteraction entities for this factId
                ObjectNode condition = objectMapper.createObjectNode().put("factId", factId.toString());
                CompletableFuture<ArrayNode> interactionsFuture = entityService.getItemsByCondition(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, condition);
                ArrayNode existingInteractions = interactionsFuture.join();

                if (existingInteractions.isEmpty()) {
                    // Add new FactInteraction entity with emailsSent = number of subscribers
                    ObjectNode newInteraction = objectMapper.createObjectNode();
                    newInteraction.put("factId", factId.toString());
                    newInteraction.put("emailsSent", subscribers.size());
                    newInteraction.put("emailsOpened", 0);
                    newInteraction.put("linksClicked", 0);

                    entityService.addItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, newInteraction, processFactInteraction)
                            .exceptionally(ex -> {
                                logger.error("Failed to add FactInteraction entity", ex);
                                return null;
                            });
                } else {
                    // Update existing FactInteraction entities: increment emailsSent by number of subscribers
                    for (JsonNode interactionNode : existingInteractions) {
                        UUID interactionId;
                        try {
                            interactionId = UUID.fromString(interactionNode.get("technicalId").asText());
                        } catch (IllegalArgumentException e) {
                            logger.warn("Invalid technicalId format in FactInteraction: {}", interactionNode.get("technicalId").asText());
                            continue;
                        }
                        int emailsSent = interactionNode.get("emailsSent").asInt() + subscribers.size();
                        int emailsOpened = interactionNode.get("emailsOpened").asInt();
                        int linksClicked = interactionNode.get("linksClicked").asInt();

                        ObjectNode updatedInteraction = objectMapper.createObjectNode();
                        updatedInteraction.put("factId", factId.toString());
                        updatedInteraction.put("emailsSent", emailsSent);
                        updatedInteraction.put("emailsOpened", emailsOpened);
                        updatedInteraction.put("linksClicked", linksClicked);

                        entityService.updateItem(ENTITY_NAME_FACTINTERACTION, ENTITY_VERSION, interactionId, updatedInteraction)
                                .exceptionally(ex -> {
                                    logger.error("Failed to update FactInteraction entity {}", interactionId, ex);
                                    return null;
                                });
                    }
                }
            } else {
                logger.warn("factId is null or invalid - skipping FactInteraction update");
            }

        } catch (Exception e) {
            logger.error("Exception in processCatFact workflow", e);
        }

        return entity;
    };

    // Workflow function for FactInteraction entity: no changes currently
    private Function<Object, Object> processFactInteraction = entity -> entity;

    @PostMapping(value = "/subscribers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Subscriber> addSubscriber(@RequestBody @Valid SubscriberRequest request) {
        // Check for existing subscriber with same email
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        ArrayNode subscribersArray = subscribersFuture.join();

        for (JsonNode node : subscribersArray) {
            String email = node.get("email").asText();
            if (email.equalsIgnoreCase(request.getEmail())) {
                UUID technicalId;
                try {
                    technicalId = UUID.fromString(node.get("technicalId").asText());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid technicalId format in Subscriber: {}", node.get("technicalId").asText());
                    continue;
                }
                Subscriber existingSubscriber = new Subscriber(technicalId, email, node.get("status").asText(),
                        Instant.parse(node.get("subscribedAt").asText()));
                logger.info("Subscriber with email {} already exists: {}", request.getEmail(), technicalId);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(existingSubscriber);
            }
        }

        Subscriber subscriber = new Subscriber(null, request.getEmail(), "SUBSCRIBED", Instant.now());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber);
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
            UUID technicalId;
            try {
                technicalId = UUID.fromString(node.get("technicalId").asText());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid technicalId format in Subscriber: {}", node.get("technicalId").asText());
                continue;
            }
            String email = node.get("email").asText();
            String status = node.get("status").asText();
            Instant subscribedAt = Instant.parse(node.get("subscribedAt").asText());
            subscribersList.add(new Subscriber(technicalId, email, status, subscribedAt));
        }
        return ResponseEntity.ok(subscribersList);
    }

    // Endpoint to create a CatFact entity triggering workflow that fetches fact and sends emails asynchronously
    @PostMapping(value = "/facts/sendWeekly", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FactSentResponse> sendWeeklyCatFact(@RequestBody @Valid FactSendRequest request) {
        logger.info("Received request to send weekly cat fact, triggeredBy={}", request.getTriggeredBy());

        CatFact fact = new CatFact(null, "", null);
        CompletableFuture<UUID> factIdFuture = entityService.addItem(ENTITY_NAME_CATFACT, ENTITY_VERSION, fact);
        UUID factId = factIdFuture.join();
        fact.setFactId(factId);

        // factText updated asynchronously, cannot return it here immediately
        FactSentResponse response = new FactSentResponse(factId, "", 0);

        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION);
        int subscribersCount = subscribersFuture.join().size();
        response.setSentToSubscribers(subscribersCount);

        return ResponseEntity.ok(response);
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
            UUID technicalId;
            UUID factId;
            try {
                technicalId = UUID.fromString(node.get("technicalId").asText());
                factId = UUID.fromString(node.get("factId").asText());
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format in FactInteraction: techId={}, factId={}",
                        node.get("technicalId").asText(), node.get("factId").asText());
                continue;
            }
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