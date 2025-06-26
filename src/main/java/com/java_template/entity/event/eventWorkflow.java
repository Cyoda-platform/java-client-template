package com.java_template.entity.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventWorkflow {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> processEvent(ObjectNode eventEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String eventType = eventEntity.path("eventType").asText("");
                String timestamp = eventEntity.path("timestamp").asText("");
                log.info("Processing event entity in workflow: eventType='{}', timestamp='{}'", eventType, timestamp);

                boolean isKeyEvent = "food_request".equalsIgnoreCase(eventType);
                eventEntity.put("detected", isKeyEvent);

                if (isKeyEvent) {
                    String notificationMsg = "Emergency! A cat demands snacks";
                    eventEntity.put("message", notificationMsg);

                    ObjectNode notificationEntity = objectMapper.createObjectNode();
                    notificationEntity.put("message", notificationMsg);
                    notificationEntity.put("timestamp", Instant.now().toString());
                    notificationEntity.put("recipient", "default_human_recipient@example.com");

                    // Add notification entity asynchronously without workflow (identity function)
                    entityService.addItem("notification", ENTITY_VERSION, notificationEntity, n -> n)
                            .thenAccept(id -> log.info("Notification created by event workflow with id={}", id))
                            .exceptionally(ex -> {
                                log.error("Failed to add notification entity in event workflow", ex);
                                return null;
                            });
                } else {
                    eventEntity.put("message", "");
                }

                return eventEntity;
            } catch (Exception e) {
                log.error("Exception in processEvent workflow", e);
                return eventEntity;
            }
        });
    }

}