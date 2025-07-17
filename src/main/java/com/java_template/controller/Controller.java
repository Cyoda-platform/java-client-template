package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    private static final String ENTITY_NAME_SUBSCRIBER = "Subscriber";

    private volatile String lastFactId;
    private volatile int factSentCount = 0;
    private final ConcurrentHashMap<String, Integer> interactionCounts = new ConcurrentHashMap<>();

    public Controller(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @PostMapping("/api/subscribe")
    public ResponseEntity<SubscribeResponse> subscribe(@RequestBody @Valid SubscribeRequest request) {
        logger.info("Received subscription request for email: {}", request.getEmail());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", request.getEmail()));

        boolean exists = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, "1.0", condition)
                .thenApply(arrayNode -> arrayNode.size() > 0)
                .join();

        if (exists) {
            logger.info("Email {} already subscribed", request.getEmail());
            return ResponseEntity.ok(new SubscribeResponse(true, "Already subscribed"));
        }

        Subscriber subscriber = new Subscriber(request.getEmail(), request.getName());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME_SUBSCRIBER, "1.0", subscriber);
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

        CompletableFuture<List<JsonNode>> subscribersFuture = entityService.getItems(ENTITY_NAME_SUBSCRIBER, "1.0")
                .thenApply(arrayNode -> arrayNode.findValues(null));
        List<JsonNode> subscribersList = entityService.getItems(ENTITY_NAME_SUBSCRIBER, "1.0").join();

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
        List<JsonNode> subscribersList = entityService.getItems(ENTITY_NAME_SUBSCRIBER, "1.0").join();
        int subscriberCount = subscribersList.size();

        int interactionCountSum = interactionCounts.values().stream().mapToInt(Integer::intValue).sum();

        logger.info("Report summary requested: subscribers={}, factSentCount={}, interactions={}",
                subscriberCount, factSentCount, interactionCountSum);
        return ResponseEntity.ok(new ReportSummaryResponse(subscriberCount, factSentCount, interactionCountSum));
    }

    @PostMapping("/api/interaction")
    public ResponseEntity<GenericResponse> recordInteraction(@RequestBody @Valid InteractionRequest request) {
        logger.info("Recording interaction: email={}, factId={}, type={}",
                request.getSubscriberEmail(), request.getFactId(), request.getInteractionType());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "EQUALS", request.getSubscriberEmail()));
        boolean exists = entityService.getItemsByCondition(ENTITY_NAME_SUBSCRIBER, "1.0", condition)
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

    public static class SubscribeRequest {
        @Email
        @NotBlank
        private String email;
        private String name;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class SubscribeResponse {
        private boolean success;
        private String message;
        public SubscribeResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SendWeeklyResponse {
        private boolean success;
        private int sentCount;
        private String fact;
        public SendWeeklyResponse(boolean success, int sentCount, String fact) {
            this.success = success;
            this.sentCount = sentCount;
            this.fact = fact;
        }
        public boolean isSuccess() { return success; }
        public int getSentCount() { return sentCount; }
        public String getFact() { return fact; }
    }

    public static class ReportSummaryResponse {
        private int subscriberCount;
        private int factSentCount;
        private int interactionCount;
        public ReportSummaryResponse(int subscriberCount, int factSentCount, int interactionCount) {
            this.subscriberCount = subscriberCount;
            this.factSentCount = factSentCount;
            this.interactionCount = interactionCount;
        }
        public int getSubscriberCount() { return subscriberCount; }
        public int getFactSentCount() { return factSentCount; }
        public int getInteractionCount() { return interactionCount; }
    }

    public static class InteractionRequest {
        @Email
        @NotBlank
        private String subscriberEmail;
        @NotBlank
        private String factId;
        @NotBlank
        private String interactionType;

        public String getSubscriberEmail() { return subscriberEmail; }
        public void setSubscriberEmail(String subscriberEmail) { this.subscriberEmail = subscriberEmail; }
        public String getFactId() { return factId; }
        public void setFactId(String factId) { this.factId = factId; }
        public String getInteractionType() { return interactionType; }
        public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    }

    public static class GenericResponse {
        private boolean success;
        public GenericResponse(boolean success) {
            this.success = success;
        }
        public boolean isSuccess() { return success; }
    }

    public static class ErrorResponse {
        private String error;
        private String message;
        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
        public String getError() { return error; }
        public String getMessage() { return message; }
    }

    public static class Subscriber {
        private final String email;
        private final String name;
        public Subscriber(String email, String name) {
            this.email = email;
            this.name = name;
        }
        public String getEmail() { return email; }
        public String getName() { return name; }
    }
}