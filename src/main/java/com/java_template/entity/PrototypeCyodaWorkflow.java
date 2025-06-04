package com.java_template.entity;

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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "subscriber";

    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized.");
    }

    /**
     * Workflow function that processes a subscriber entity before persistence.
     * This function is async and can modify entity state or add supplementary entities.
     *
     * @param entity the subscriber entity as ObjectNode
     * @return CompletableFuture of modified entity
     */
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // Defensive check: email must be present and valid
        if (!entity.hasNonNull("email") || entity.get("email").asText().isBlank()) {
            CompletableFuture<ObjectNode> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new IllegalArgumentException("Email is required in subscriber entity"));
            return failedFuture;
        }

        // Set default status if missing or blank
        if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
            entity.put("status", "subscribed");
        }

        // Enrich entity with welcomeCatFact from external API (best effort)
        try {
            String catFactJsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (catFactJsonStr != null) {
                JsonNode catFactJson = objectMapper.readTree(catFactJsonStr);
                String fact = catFactJson.path("fact").asText(null);
                if (fact != null) {
                    entity.put("welcomeCatFact", fact);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch cat fact in workflow function: {}", e.getMessage());
            // ignore failure to enrich
        }

        // Fire and forget async task: send welcome email to this subscriber
        CompletableFuture.runAsync(() -> sendWelcomeEmail(entity));

        // Return the modified entity wrapped in CompletableFuture
        return CompletableFuture.completedFuture(entity);
    }

    /**
     * Fire and forget method that sends a welcome email.
     * This method should never modify the current entity in database.
     *
     * @param entity subscriber entity as ObjectNode
     */
    @Async
    void sendWelcomeEmail(ObjectNode entity) {
        String email = entity.path("email").asText(null);
        if (email == null || email.isBlank()) {
            logger.warn("No email found in entity, skipping welcome email");
            return;
        }
        // Simulate sending email
        logger.info("Sending welcome email to {}", email);

        // Insert real email sending logic here (SMTP, API, etc.)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }

        logger.info("Welcome email sent to {}", email);
    }

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<Subscriber>> createSubscriber(@RequestBody @Valid SubscriberRequest request) {
        logger.info("Received subscriber creation request for email: {}", request.getEmail());

        // Check for duplicates first
        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);

        return allSubscribersFuture.thenCompose(allSubscribers -> {
            boolean alreadyExists = false;
            for (JsonNode node : allSubscribers) {
                String email = node.path("email").asText();
                if (email.equalsIgnoreCase(request.getEmail())) {
                    alreadyExists = true;
                    break;
                }
            }
            if (alreadyExists) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Subscriber with this email already exists");
            }

            // Create ObjectNode for new subscriber entity
            ObjectNode subscriberNode = objectMapper.createObjectNode();
            subscriberNode.put("email", request.getEmail());
            // status and welcomeCatFact will be set in workflow

            // Pass workflow function as argument
            return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriberNode, this::processSubscriber)
                    .thenApply(technicalId -> {
                        // Construct response entity using final entity state from subscriberNode
                        Subscriber subscriber = new Subscriber(technicalId.toString(), request.getEmail(),
                                subscriberNode.path("status").asText("subscribed"));
                        logger.info("Subscriber created with technicalId: {}", technicalId);
                        return ResponseEntity.created(URI.create("/api/cyodaentity/subscribers/" + technicalId)).body(subscriber);
                    });
        });
    }

    @GetMapping("/subscribers/{subscriberId}")
    public CompletableFuture<ResponseEntity<Subscriber>> getSubscriber(@PathVariable String subscriberId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(subscriberId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid subscriber ID format");
        }
        CompletableFuture<ObjectNode> subscriberFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);

        return subscriberFuture.thenApply(node -> {
            if (node == null || node.isEmpty()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscriber not found");
            }
            Subscriber subscriber = new Subscriber();
            subscriber.setSubscriberId(node.path("technicalId").asText());
            subscriber.setEmail(node.path("email").asText());
            subscriber.setStatus(node.path("status").asText());
            logger.info("Returning subscriber info for technicalId: {}", subscriberId);
            return ResponseEntity.ok(subscriber);
        });
    }

    @PostMapping("/facts/sendWeekly")
    public ResponseEntity<FactSendResponse> sendWeeklyCatFact() {
        logger.info("Triggering weekly cat fact ingestion and email sending.");

        JsonNode catFactJson;
        try {
            String responseString = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            catFactJson = objectMapper.readTree(responseString);
        } catch (Exception e) {
            logger.error("Failed to retrieve cat fact from external API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to retrieve cat fact");
        }

        String catFactText = catFactJson.path("fact").asText(null);
        if (catFactText == null) {
            logger.error("Cat fact missing in API response");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Invalid cat fact response");
        }

        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);

        int subscriberCount = 0;
        try {
            // block here to get subscriber count synchronously
            ArrayNode subscribersNode = allSubscribersFuture.get();
            subscriberCount = subscribersNode.size();
        } catch (Exception e) {
            logger.error("Failed to get subscribers for count", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get subscribers");
        }

        // Fire and forget async email sending - not part of entity workflow since not entity persistence
        sendWeeklyCatFactEmails(catFactText);

        FactSendResponse response = new FactSendResponse(subscriberCount, catFactText);
        logger.info("Cat fact sent to {} subscribers", subscriberCount);
        return ResponseEntity.ok(response);
    }

    @Async
    void sendWeeklyCatFactEmails(String catFactText) {
        CompletableFuture<ArrayNode> allSubscribersFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        allSubscribersFuture.thenAccept(subscribersNode -> {
            logger.info("Sending cat fact emails to {} subscribers...", subscribersNode.size());
            for (JsonNode node : subscribersNode) {
                String email = node.path("email").asText();
                if (email != null && !email.isBlank()) {
                    logger.info("Sending weekly cat fact email to {}: {}", email, catFactText);
                    // Actual email sending logic should be here
                }
            }
        }).exceptionally(ex -> {
            logger.error("Failed to send weekly cat fact emails", ex);
            return null;
        });
    }

    @GetMapping("/report/subscribers")
    public CompletableFuture<ResponseEntity<SubscriberReport>> getSubscriberReport() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION).thenApply(subscribersNode -> {
            int total = subscribersNode.size();
            logger.info("Subscriber report requested, total: {}", total);
            return ResponseEntity.ok(new SubscriberReport(total));
        });
    }

    @GetMapping("/report/interaction")
    public ResponseEntity<InteractionReport> getInteractionReport() {
        // Interaction report currently static values, extend if needed
        int emailOpens = 0;
        int linkClicks = 0;
        InteractionReport report = new InteractionReport(emailOpens, linkClicks);
        logger.info("Interaction report requested: opens={}, clicks={}", emailOpens, linkClicks);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
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
    @NoArgsConstructor
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