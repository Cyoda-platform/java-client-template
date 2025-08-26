package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();

        try {
            // Build a simple search condition to find existing laureates by laureateId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.laureateId", "EQUALS", entity.getLaureateId())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();

            if (results != null && results.size() > 0) {
                // There is at least one existing record; perform basic deduplication based on rawPayload
                ObjectNode existing = (ObjectNode) results.get(0);
                String existingRaw = existing.has("rawPayload") && !existing.get("rawPayload").isNull()
                    ? existing.get("rawPayload").asText()
                    : null;
                String currentRaw = entity.getRawPayload();

                if (existingRaw != null && existingRaw.equals(currentRaw)) {
                    // No change detected compared to stored record
                    entity.setChangeType(entity.getChangeType() != null ? entity.getChangeType() : "unchanged");
                    entity.setPublished(Boolean.FALSE);
                    logger.info("Laureate {} unchanged - skipping publish", entity.getLaureateId());
                } else {
                    // Content changed -> mark as updated and publish for notifications
                    entity.setChangeType("updated");
                    entity.setPublished(Boolean.TRUE);
                    logger.info("Laureate {} marked as updated - will be published", entity.getLaureateId());
                }
            } else {
                // No existing record -> new laureate
                entity.setChangeType(entity.getChangeType() != null ? entity.getChangeType() : "new");
                entity.setPublished(Boolean.TRUE);
                logger.info("Laureate {} is new - will be published", entity.getLaureateId());
            }

            // Additional enrichment or side-effects may be added here in future.
            // IMPORTANT: Do not call updateItem on Laureate entity. The entity state changes above
            // will be persisted automatically by Cyoda based on the workflow.

        } catch (Exception ex) {
            logger.error("Error during PersistLaureateProcessor processing for laureateId={} : {}", entity.getLaureateId(), ex.getMessage(), ex);
            // On error, ensure we don't accidentally mark as published
            entity.setPublished(Boolean.FALSE);
            // Optionally mark changeType to indicate failure to persist/inspect
            if (entity.getChangeType() == null || entity.getChangeType().isBlank()) {
                entity.setChangeType("error");
            }
        }

        return entity;
    }
}