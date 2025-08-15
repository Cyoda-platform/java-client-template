package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class PersistStateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistStateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PersistStateProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting HackerNewsItem state for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        // The actual persistence integration should be provided by the application's repository layer.
        // Use EntityService to persist or update the item
        try {
            // Try to find existing item by matching originalJson
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> found = entityService.getItemsByCondition(
                HackerNewsItem.ENTITY_NAME,
                String.valueOf(HackerNewsItem.ENTITY_VERSION),
                com.java_template.common.util.SearchConditionRequest.group("AND", com.java_template.common.util.Condition.of("$.originalJson", "EQUALS", entity.getOriginalJson())),
                true
            );
            com.fasterxml.jackson.databind.node.ArrayNode arr = found.get(10, TimeUnit.SECONDS);
            if (arr != null && arr.size() > 0) {
                ObjectNode stored = (ObjectNode) arr.get(0);
                if (stored.has("technicalId")) {
                    UUID technical = UUID.fromString(stored.get("technicalId").asText());
                    entityService.updateItem(HackerNewsItem.ENTITY_NAME, String.valueOf(HackerNewsItem.ENTITY_VERSION), technical, entity).get(10, TimeUnit.SECONDS);
                    logger.info("Updated HackerNewsItem technicalId={}", technical);
                }
            } else {
                UUID added = entityService.addItem(HackerNewsItem.ENTITY_NAME, String.valueOf(HackerNewsItem.ENTITY_VERSION), entity).get(10, TimeUnit.SECONDS);
                logger.info("Persisted new HackerNewsItem technicalId={}", added);
            }
        } catch (Exception e) {
            logger.error("Failed to persist HackerNewsItem: {}", e.getMessage());
        }
        return entity;
    }
}
