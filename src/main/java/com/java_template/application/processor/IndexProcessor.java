package com.java_template.application.processor;
import com.java_template.application.entity.storeditem.version_1.StoredItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.nio.charset.StandardCharsets;

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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(StoredItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Basic indexing operations:
        // - Ensure hnItem exists; if not, log and leave entity as-is (validation should have caught it)
        // - Calculate and set sizeBytes based on hnItem UTF-8 byte length
        // - Ensure storedAt timestamp exists; if missing, set to now
        // Any changes to this entity will be persisted automatically by Cyoda.

        try {
            logger.info("IndexProcessor started for StoredItem: {}", entity.getStorageTechnicalId());

            String hnJson = entity.getHnItem();
            if (hnJson == null || hnJson.isBlank()) {
                logger.warn("StoredItem.hnItem is empty for storageTechnicalId={}", entity.getStorageTechnicalId());
                return entity;
            }

            int bytes = hnJson.getBytes(StandardCharsets.UTF_8).length;
            if (entity.getSizeBytes() == null || entity.getSizeBytes() != bytes) {
                entity.setSizeBytes(bytes);
                logger.debug("Updated sizeBytes={} for storageTechnicalId={}", bytes, entity.getStorageTechnicalId());
            }

            if (entity.getStoredAt() == null || entity.getStoredAt().isBlank()) {
                String now = Instant.now().toString();
                entity.setStoredAt(now);
                logger.debug("Set storedAt={} for storageTechnicalId={}", now, entity.getStorageTechnicalId());
            }

            // Indexing completed - any additional index metadata would be set here if model supported it.
            logger.info("IndexProcessor completed for StoredItem: {}", entity.getStorageTechnicalId());
        } catch (Exception ex) {
            logger.error("Error during IndexProcessor for StoredItem: {}", entity.getStorageTechnicalId(), ex);
            // Do not modify entity status here; leave error handling to other processors/criteria if needed.
        }

        return entity;
    }
}