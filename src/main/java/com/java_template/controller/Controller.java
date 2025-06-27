package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("eventType", request.getEventType());
        eventNode.put("eventDescription", request.getEventDescription());
        eventNode.put("timestamp", eventInstant.toString());
        eventNode.put("notificationStatus", "pending");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CatEvent",
                ENTITY_VERSION,
                eventNode
        );

        UUID technicalId = idFuture.join();

        return ResponseEntity.ok(new CatEventDetectResponse("success", "Notification process started", technicalId.toString()));
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