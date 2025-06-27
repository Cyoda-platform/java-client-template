package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("cyoda/cat_event")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEventDetectRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String eventType;

        @NotBlank
        @Size(max = 200)
        private String eventDescription;

        @NotBlank
        private String timestamp; // ISO8601
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEventDetectResponse {
        private String status;
        private String message;
        private String eventId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatEvent {
        @JsonIgnore
        private UUID technicalId;

        private String eventType;
        private String eventDescription;
        private Instant timestamp;
        private String notificationStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }

    @PostMapping("/detect")
    public ResponseEntity<CatEventDetectResponse> detectCatEvent(@RequestBody @Valid CatEventDetectRequest request) {
        logger.info("Received detection request: {}", request);
        Instant eventInstant;
        try {
            eventInstant = Instant.parse(request.getTimestamp());
        } catch (Exception e) {
            logger.error("Invalid timestamp: {}", request.getTimestamp(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timestamp must be ISO8601");
        }

        CatEvent event = new CatEvent(null,
                request.getEventType(),
                request.getEventDescription(),
                eventInstant,
                "pending");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CatEvent",
                ENTITY_VERSION,
                event
        );

        UUID technicalId = idFuture.join();
        event.setTechnicalId(technicalId);

        CompletableFuture.runAsync(() -> processEventAndNotify(event))
                .exceptionally(ex -> {
                    logger.error("Processing error for {}: {}", technicalId, ex.getMessage(), ex);
                    event.setNotificationStatus("failed");
                    updateNotificationStatus(event);
                    return null;
                });

        return ResponseEntity.ok(new CatEventDetectResponse("success", "Notification sent", technicalId.toString()));
    }

    private void updateNotificationStatus(CatEvent event) {
        entityService.updateItem("CatEvent", ENTITY_VERSION, event.getTechnicalId(), event);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<CatEvent> getCatEvent(@PathVariable @NotBlank String eventId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(eventId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format for eventId");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("CatEvent", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.join();
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cat event not found with id " + eventId);
        }
        CatEvent event = convertNodeToCatEvent(node);
        return ResponseEntity.ok(event);
    }

    @GetMapping
    public ResponseEntity<List<CatEvent>> listCatEvents(
            @RequestParam(required = false) @Size(min = 3, max = 50) String eventType,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        SearchConditionRequest condition = null;
        if (eventType != null) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.eventType", "EQUALS", eventType));
        }

        CompletableFuture<ArrayNode> itemsFuture;
        if (condition == null) {
            itemsFuture = entityService.getItems("CatEvent", ENTITY_VERSION);
        } else {
            itemsFuture = entityService.getItemsByCondition("CatEvent", ENTITY_VERSION, condition);
        }

        ArrayNode nodes = itemsFuture.join();
        List<CatEvent> events = new ArrayList<>();
        nodes.forEach(node -> events.add(convertNodeToCatEvent((ObjectNode) node)));

        events.sort(Comparator.comparing(CatEvent::getTimestamp).reversed());
        if (events.size() > limit) {
            events = events.subList(0, limit);
        }
        return ResponseEntity.ok(events);
    }

    @Async
    void processEventAndNotify(CatEvent event) {
        try {
            Thread.sleep(500);
            sendNotification(event);
            event.setNotificationStatus("sent");
            updateNotificationStatus(event);
        } catch (InterruptedException e) {
            logger.error("Interrupted processing {}", event.getTechnicalId(), e);
            Thread.currentThread().interrupt();
            event.setNotificationStatus("failed");
            updateNotificationStatus(event);
        }
    }

    void sendNotification(CatEvent event) {
        String msg = String.format("Emergency! A cat demands snacks (Type: %s, Description: %s)",
                event.getEventType(), event.getEventDescription());
        logger.info("Sending notification: {}", msg);
        // TODO: integrate real notification channel
    }

    private CatEvent convertNodeToCatEvent(ObjectNode node) {
        CatEvent event = new CatEvent();
        if (node.hasNonNull("technicalId")) {
            event.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.hasNonNull("eventType")) {
            event.setEventType(node.get("eventType").asText());
        }
        if (node.hasNonNull("eventDescription")) {
            event.setEventDescription(node.get("eventDescription").asText());
        }
        if (node.hasNonNull("timestamp")) {
            try {
                event.setTimestamp(Instant.parse(node.get("timestamp").asText()));
            } catch (Exception ignored) {
            }
        }
        if (node.hasNonNull("notificationStatus")) {
            event.setNotificationStatus(node.get("notificationStatus").asText());
        }
        return event;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleException(ResponseStatusException ex) {
        ErrorResponse err = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }
}