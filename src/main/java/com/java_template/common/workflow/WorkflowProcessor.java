package com.java_template.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Workflow dispatcher that routes events to individual CyodaProcessor beans via ProcessorFactory.
 * Each workflow method is implemented as a separate processor class with bean name matching the method name.
 * Handles ObjectNode ↔ CyodaEntity conversion at the workflow boundary.
 */
@Component
public class WorkflowProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowProcessor.class);

    private final ProcessorFactory processorFactory;
    private final EntityTypeResolver entityTypeResolver;

    public WorkflowProcessor(ProcessorFactory processorFactory, EntityTypeResolver entityTypeResolver) {
        this.processorFactory = processorFactory;
        this.entityTypeResolver = entityTypeResolver;
        logger.info("WorkflowProcessor initialized with ProcessorFactory containing {} processors",
                   processorFactory.getProcessorCount());
    }

    /**
     * Processes an event by dispatching to the appropriate CyodaProcessor via factory.
     * Handles ObjectNode ↔ CyodaEntity conversion at the workflow boundary.
     * @param eventName the name of the event/method (must match a processor name)
     * @param payload the payload to process
     * @return CompletableFuture containing the processed result
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<ObjectNode> processEvent(String eventName, ObjectNode payload) {
        // Use toLowerCase for case-insensitive bean lookup
        String processorName = eventName.toLowerCase();
        CyodaProcessor<? extends CyodaEntity> processor = processorFactory.getProcessor(processorName);

        if (processor == null) {
            logger.warn("No processor found for event '{}' (normalized: '{}'). Available processors: {}",
                       eventName, processorName, String.join(", ", processorFactory.getRegisteredProcessors()));
            payload.put("success", false);
            payload.put("error", "No processor found for event: " + eventName);
            return CompletableFuture.completedFuture(payload);
        }

        // Get the entity type for this processor
        Class<? extends CyodaEntity> entityType = processorFactory.getEntityType(processorName);
        if (entityType == null) {
            logger.error("No entity type found for processor '{}'", processorName);
            payload.put("success", false);
            payload.put("error", "No entity type found for processor: " + processorName);
            return CompletableFuture.completedFuture(payload);
        }

        logger.debug("Dispatching event '{}' to processor '{}' with entity type '{}'",
                    eventName, processorName, entityType.getSimpleName());

        try {
            // Convert ObjectNode to entity
            CyodaEntity entity = entityTypeResolver.convertToEntity(payload, entityType);

            // Process the entity (cast is safe because we got the type from the processor)
            CyodaProcessor<CyodaEntity> typedProcessor = (CyodaProcessor<CyodaEntity>) processor;
            CompletableFuture<CyodaEntity> processedEntityFuture = typedProcessor.process(entity);

            // Convert result back to ObjectNode
            return processedEntityFuture.thenApply(processedEntity -> {
                try {
                    return entityTypeResolver.convertToObjectNode(processedEntity);
                } catch (Exception e) {
                    logger.error("Error converting processed entity back to ObjectNode: {}", e.getMessage(), e);
                    payload.put("success", false);
                    payload.put("error", "Entity conversion failed: " + e.getMessage());
                    return payload;
                }
            });

        } catch (Exception e) {
            logger.error("Error processing event '{}': {}", eventName, e.getMessage(), e);
            payload.put("success", false);
            payload.put("error", "Processing failed: " + e.getMessage());
            return CompletableFuture.completedFuture(payload);
        }
    }

    /**
     * Gets all available processor names.
     * @return array of processor names
     */
    public String[] getAvailableProcessors() {
        return processorFactory.getRegisteredProcessors();
    }
}