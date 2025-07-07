package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";
    private static final String ENTITY_NAME = "subscriber";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberRequest {
        @NotBlank(message = "Email is mandatory")
        @Email(message = "Invalid email format")
        private String email;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        // technicalId is managed by entityService, ignore in JSON serialization to avoid conflicts
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String email;
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberResponse {
        private UUID subscriberId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendWeeklyResponse {
        private String status;
        private int sentCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriberCountResponse {
        private int totalSubscribers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionSummary {
        private int emailOpens;
        private int linkClicks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionReportResponse {
        private InteractionSummary interactions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Interaction {
        private UUID subscriberTechnicalId;
        private boolean emailOpened;
        private int linkClicks;
        private Instant lastInteractionAt;
    }

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscriberResponse>> subscribe(@Valid @RequestBody SubscriberRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Check if email already subscribed via entityService.getItemsByCondition
        Condition condition = Condition.of("$.email", "EQUALS", email);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCondition)
                .thenCompose(results -> {
                    for (JsonNode node : results) {
                        String existingEmail = node.path("email").asText("");
                        if (existingEmail.equals(email)) {
                            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Email already subscribed");
                        }
                    }
                    Subscriber subscriber = new Subscriber(null, email, request.getName(), Instant.now());
                    return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriber)
                            .thenApply(id -> {
                                logger.info("New subscriber registered: {}", email);
                                return ResponseEntity.ok(new SubscriberResponse(id, "Subscription successful"));
                            });
                });
    }

    @PostMapping("/facts/send-weekly")
    public CompletableFuture<ResponseEntity<SendWeeklyResponse>> sendWeeklyCatFact() {
        logger.info("Triggered weekly cat fact send-out");

        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(results -> {
                    String fact = null;
                    try {
                        JsonNode catFactJson = fetchCatFactFromExternalAPI();
                        fact = catFactJson.path("fact").asText(null);
                        if (fact == null || fact.isEmpty()) {
                            logger.error("CatFact API returned empty fact");
                            return ResponseEntity.ok(new SendWeeklyResponse("failed", 0));
                        }
                    } catch (ResponseStatusException e) {
                        logger.error("Error fetching cat fact", e);
                        return ResponseEntity.ok(new SendWeeklyResponse("failed", 0));
                    }

                    int sentCount = 0;
                    for (JsonNode node : results) {
                        UUID technicalId = UUID.fromString(node.path("technicalId").asText());
                        String name = node.path("name").asText("");
                        String email = node.path("email").asText("");
                        sendEmailMock(name, email, fact);
                        sentCount++;
                    }
                    logger.info("Sent cat fact email to {} subscribers", sentCount);
                    return ResponseEntity.ok(new SendWeeklyResponse("success", sentCount));
                });
    }

    @GetMapping("/report/subscribers/count")
    public CompletableFuture<ResponseEntity<SubscriberCountResponse>> getSubscriberCount() {
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(results -> {
                    int count = results.size();
                    logger.info("Subscriber count requested: {}", count);
                    return ResponseEntity.ok(new SubscriberCountResponse(count));
                });
    }

    @GetMapping("/report/interactions")
    public ResponseEntity<InteractionReportResponse> getInteractionReport() {
        // Since interactions were previously stored in-memory, now no source provided.
        // Skipping interaction aggregation logic as no external service mentioned for interactions.
        // Returning zeros.
        logger.info("Interaction report requested: opens=0, clicks=0");
        InteractionSummary summary = new InteractionSummary(0, 0);
        return ResponseEntity.ok(new InteractionReportResponse(summary));
    }

    private JsonNode fetchCatFactFromExternalAPI() {
        try {
            String jsonStr = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            if (jsonStr == null) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Empty response from CatFact API");
            }
            return objectMapper.readTree(jsonStr);
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact from external API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }
    }

    private void sendEmailMock(String name, String email, String fact) {
        // TODO: Replace this mock with real email sending logic
        logger.info("Sending cat fact email to {} <{}>: Fact: {}", name, email, fact);
        // No interaction tracking possible without external service, skipping.
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}