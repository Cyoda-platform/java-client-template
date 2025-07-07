package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "subscriber";
    private static final int ENTITY_VERSION = ENTITY_VERSION; // imported constant

    private static final String WEEKLY_CAT_FACT_ENTITY = "weeklyCatFact";
    private static final int WEEKLY_CAT_FACT_VERSION = 1;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<SubscriberResponse>> subscribe(@Valid @RequestBody SubscriberRequest request) {
        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("email", request.getEmail());
        if (request.getName() != null) {
            subscriberNode.put("name", request.getName());
        }
        // Removed workflow argument
        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriberNode)
                .thenApply(id -> {
                    logger.info("New subscriber registered: {}", request.getEmail());
                    return ResponseEntity.ok(new SubscriberResponse(id, "Subscription successful"));
                });
    }

    @PostMapping("/facts/send-weekly")
    public CompletableFuture<ResponseEntity<SendWeeklyResponse>> sendWeeklyCatFact() {
        ObjectNode triggerEntity = objectMapper.createObjectNode();
        triggerEntity.put("triggeredAt", Instant.now().toString());
        // Removed workflow argument
        return entityService.addItem(WEEKLY_CAT_FACT_ENTITY, WEEKLY_CAT_FACT_VERSION, triggerEntity)
                .thenApply(id -> {
                    logger.info("Triggered weekly cat fact send-out");
                    return ResponseEntity.ok(new SendWeeklyResponse("success", -1));
                })
                .exceptionally(ex -> {
                    logger.error("Failed to send weekly cat fact", ex);
                    return ResponseEntity.ok(new SendWeeklyResponse("failed", 0));
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
        logger.info("Interaction report requested: opens=0, clicks=0");
        InteractionSummary summary = new InteractionSummary(0, 0);
        return ResponseEntity.ok(new InteractionReportResponse(summary));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }

    // The following workflow functions and email sending are removed in this version
}