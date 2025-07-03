package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("prototype")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
        logger.info("Workflow prototype initialized");
    }

    @PostConstruct
    public void init() {
        logger.info("Workflow post construct init");
    }

    // Action function for 'start_processing' transition
    public CompletableFuture<ObjectNode> enrichEntity(ObjectNode entity) {
        entity.put("processedTimestamp", Instant.now().toString());

        UUID entityId = null;
        try {
            if (entity.hasNonNull("id")) {
                entityId = UUID.fromString(entity.get("id").asText());
            }
        } catch (IllegalArgumentException ignored) {
            // No valid UUID id in entity, ignore
        }

        CompletableFuture<ObjectNode> metadataFuture = entityService.getItem(
                "metadata", ENTITY_VERSION, UUID.fromString("00000000-0000-0000-0000-000000000000"))
            .exceptionally(ex -> {
                logger.warn("Failed to fetch metadata entity in workflow", ex);
                return null;
            });

        CompletableFuture<Void> notificationFuture = CompletableFuture.runAsync(() -> {
            try {
                logger.info("Sending notification for prototype entity asynchronously");
                // TODO: Replace with real notification logic or message queue
            } catch (Exception e) {
                logger.error("Error during async notification in workflow", e);
            }
        });

        return metadataFuture.thenCompose(metadata -> {
            if (metadata != null && !metadata.isEmpty(null)) {
                entity.set("metadata", metadata);
            }
            return notificationFuture.handle((v, ex) -> {
                if (ex != null) {
                    logger.error("Notification future completed exceptionally", ex);
                }
                return entity;
            });
        });
    }

    // Condition function for 'to_error' transition
    public boolean conditionProcessingFailed(ObjectNode entity) {
        // Define failure condition logic
        // For prototype, no explicit failure logic, return false to avoid error transition
        // TODO: Implement real failure detection logic if needed
        return false;
    }

    // Condition function for 'to_processed' transition
    public boolean conditionIsProcessed(ObjectNode entity) {
        return entity.hasNonNull("processedTimestamp");
    }
}