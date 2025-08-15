package com.java_template.application.processor;

import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.application.store.InMemoryHnItemStore;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SaveHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SaveHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final InMemoryHnItemStore store;

    public SaveHnItemProcessor(SerializerFactory serializerFactory, InMemoryHnItemStore store) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.store = store;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem save for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid HackerNewsItem: missing id or type")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        if (entity == null) return false;
        Integer id = entity.getId();
        String type = entity.getType();
        if (id == null) return false;
        if (id < 0) return false;
        if (type == null) return false;
        if (type.trim().isEmpty()) return false;
        return true;
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        // Add importTimestamp as ISO-8601 in UTC with seconds precision
        String importTs = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        try {
            entity.setImportTimestamp(importTs);
        } catch (Exception e) {
            // If the entity does not have setter, log and continue (should not happen in expected model)
            logger.warn("Failed to set importTimestamp on entity: {}", e.getMessage());
        }

        // Persist (upsert) the entity into the in-memory store
        boolean created = store.upsert(entity);
        logger.info("Saved HackerNewsItem id={} created={}", entity.getId(), created);
        return entity;
    }
}
