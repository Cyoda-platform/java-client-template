package com.java_template.entity.notification;

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
public class NotificationWorkflow {

    private final EntityService entityService;

    /**
     * Workflow function to process Notification entity asynchronously prior to persistence.
     * Accepts notification entity as ObjectNode, modifies state directly.
     * Can add/get entities of different entityModels but MUST NOT modify current notification entityModel via entityService.
     * Returns the modified entity for persistence.
     */
    public CompletableFuture<ObjectNode> processNotification(ObjectNode notificationEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing notification workflow for entity: {}", notificationEntity);

                // Add processed timestamp to entity directly
                notificationEntity.put("processedTimestamp", Instant.now().toString());

                // Create supplementary entity for logging purposes
                ObjectNode logEntity = notificationEntity.objectNode();
                logEntity.put("notificationId", notificationEntity.path("notificationId").asText());
                logEntity.put("logMessage", "Notification processed asynchronously");
                logEntity.put("logTimestamp", Instant.now().toString());

                // Add supplementary entity of different entityModel "notification_log"
                entityService.addItem("notification_log", ENTITY_VERSION, logEntity);

                // Additional async logic can be added here if needed

                return notificationEntity;
            } catch (Exception e) {
                log.error("Error during notification workflow processing", e);
                // Return entity unchanged on error to avoid breaking persistence
                return notificationEntity;
            }
        });
    }
}