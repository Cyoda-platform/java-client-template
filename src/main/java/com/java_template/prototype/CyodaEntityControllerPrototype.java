package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/prototype")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";

    private volatile String lastFactId;
    private volatile String lastFactText;
    private volatile int factSentCount = 0;

    @PostMapping("/api/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        // Check if subscriber exists using entityService.getItemsByCondition
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", request.getEmail()));
        CompletableFuture<List<JsonNode>> existingFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> arrayNode.findValues(null));

        CompletableFuture<Boolean> existsFuture = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> arrayNode.size() > 0);

        boolean exists = existsFuture.join();
        if (exists) {
            logger.info("Email {} already subscribed", request.getEmail());
            return ResponseEntity.ok(new SubscribeResponse(true, "Already subscribed"));
        }

        Subscriber subscriber = new Subscriber(request.getEmail(), request.getName());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, subscriber);
        idFuture.join();

        logger.info("Subscription successful for email: {}", request.getEmail());
        return ResponseEntity.ok(new SubscribeResponse(true, "Subscription successful"));
    }

    @PostMapping("/api/facts/sendWeekly")
    public ResponseEntity<SendWeeklyResponse> sendWeeklyCatFact() {
        logger.info("Triggered weekly cat fact ingestion and sending");
        JsonNode catFactJson;
        try {
            String response = restTemplate.getForObject(CAT_FACT_API_URL, String.class);
            catFactJson = objectMapper.readTree(response);
        } catch (Exception ex) {
            logger.error("Error fetching cat fact from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to retrieve cat fact from external API");
        }
        String factText = catFactJson.path("fact").asText(null);
        if (factText == null || factText.isEmpty()) {
            logger.error("Cat fact missing or empty in API response");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Invalid cat fact received from external API");
        }

        lastFactId = UUID.randomUUID().toString();
        lastFactText = factText;

        // Fetch all subscribers from entityService
        CompletableFuture<List<JsonNode>> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(arrayNode -> arrayNode.findValues(null));
        List<JsonNode> subscribersList = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).join();

        CompletableFuture.runAsync(() -> {
            logger.info("Sending cat fact email to {} subscribers", subscribersList.size());
            subscribersList.forEach(subscriberNode -> {
                String email = subscriberNode.path("email").asText();
                logger.info("Sending cat fact email to: {} | Fact: {}", email, factText);
            });
            logger.info("Finished sending cat fact emails");
        });

        factSentCount++;
        logger.info("Weekly cat fact sent successfully. Sent count: {}", factSentCount);
        return ResponseEntity.ok(new SendWeeklyResponse(true, subscribersList.size(), factText));
    }

    @GetMapping("/api/report/summary")
    public ResponseEntity<ReportSummaryResponse> getReportSummary() {
        // Get subscriber count from entityService
        CompletableFuture<List<JsonNode>> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION)
                .thenApply(arrayNode -> arrayNode.findValues(null));
        List<JsonNode> subscribersList = entityService.getItems(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION).join();
        int subscriberCount = subscribersList.size();

        // Interaction counts are local cache, keep as volatile int or map (not externalized)
        // For simplicity, keep interaction counts in memory
        int interactionCountSum = interactionCounts.values().stream().mapToInt(Integer::intValue).sum();

        logger.info("Report summary requested: subscribers={}, factSentCount={}, interactions={}",
                subscriberCount, factSentCount, interactionCountSum);
        return ResponseEntity.ok(new ReportSummaryResponse(subscriberCount, factSentCount, interactionCountSum));
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Integer> interactionCounts = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/api/interaction")
    public ResponseEntity<GenericResponse> recordInteraction(@RequestBody @Valid InteractionRequest request) {
        logger.info("Recording interaction: email={}, factId={}, type={}",
                request.getSubscriberEmail(), request.getFactId(), request.getInteractionType());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", request.getSubscriberEmail()));
        boolean exists = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, ENTITY_VERSION, condition)
                .thenApply(arrayNode -> arrayNode.size() > 0)
                .join();

        if (!exists) {
            logger.error("Interaction record failed, subscriber email not found: {}", request.getSubscriberEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscriber email not found");
        }
        if (lastFactId == null || !lastFactId.equals(request.getFactId())) {
            logger.warn("Interaction factId does not match last sent factId");
        }
        interactionCounts.merge(request.getInteractionType(), 1, Integer::sum);
        logger.info("Interaction recorded successfully");
        return ResponseEntity.ok(new GenericResponse(true));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Unexpected exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "Internal server error"));
    }

    @Data
    public static class SubscribeRequest {
        @Email
        @NotBlank
        private String email;
        private String name;
    }

    @Data
    public static class SubscribeResponse {
        private boolean success;
        private String message;
        public SubscribeResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    @Data
    public static class SendWeeklyResponse {
        private boolean success;
        private int sentCount;
        private String fact;
        public SendWeeklyResponse(boolean success, int sentCount, String fact) {
            this.success = success;
            this.sentCount = sentCount;
            this.fact = fact;
        }
    }

    @Data
    public static class ReportSummaryResponse {
        private int subscriberCount;
        private int factSentCount;
        private int interactionCount;
        public ReportSummaryResponse(int subscriberCount, int factSentCount, int interactionCount) {
            this.subscriberCount = subscriberCount;
            this.factSentCount = factSentCount;
            this.interactionCount = interactionCount;
        }
    }

    @Data
    public static class InteractionRequest {
        @Email
        @NotBlank
        private String subscriberEmail;
        @NotBlank
        private String factId;
        @NotBlank
        private String interactionType;
    }

    @Data
    public static class GenericResponse {
        private boolean success;
        public GenericResponse(boolean success) {
            this.success = success;
        }
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }

    @Data
    public static class Subscriber {
        private final String email;
        private final String name;
    }
}