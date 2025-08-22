package com.java_template.application.processor;

import com.java_template.application.entity.storeditem.version_1.StoredItem;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class IndexProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing StoredItem for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(StoredItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(StoredItem entity) {
        return entity != null && entity.isValid();
    }

    private StoredItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<StoredItem> context) {
        StoredItem entity = context.entity();

        // Compute and fill sizeBytes if missing using UTF-8 byte length of hnItem
        try {
            if (entity.getSizeBytes() == null) {
                String hnItem = entity.getHnItem();
                if (hnItem != null) {
                    int bytes = hnItem.getBytes(StandardCharsets.UTF_8).length;
                    entity.setSizeBytes(bytes);
                    logger.info("Computed sizeBytes={} for StoredItem {}", bytes, entity.getStorageTechnicalId());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to compute sizeBytes for StoredItem {}: {}", entity.getStorageTechnicalId(), e.getMessage());
        }

        // Ensure storedAt timestamp is present
        if (entity.getStoredAt() == null || entity.getStoredAt().isBlank()) {
            String now = Instant.now().toString();
            entity.setStoredAt(now);
            logger.info("Set storedAt={} for StoredItem {}", now, entity.getStorageTechnicalId());
        }

        // Indexing step: In this implementation we don't create external indexes,
        // but we ensure entity metadata relevant for indexing is populated (sizeBytes, storedAt).
        // Additional indexing logic (external index services) should be performed by separate services.

        return entity;
    }
}