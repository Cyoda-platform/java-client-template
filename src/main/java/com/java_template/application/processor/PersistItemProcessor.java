package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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

@Component
public class PersistItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HNItem entity) {
        return entity != null && entity.isValid();
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        if (entity == null) {
            logger.warn("Received null HNItem in PersistItemProcessor");
            return null;
        }

        try {
            // Ensure required persisted data exists before marking as STORED.
            if (entity.getOriginalJson() == null || entity.getOriginalJson().isBlank()) {
                logger.warn("HNItem {} missing originalJson, marking as FAILED", entity.getId());
                entity.setStatus("FAILED");
                return entity;
            }

            if (entity.getImportTimestamp() == null || entity.getImportTimestamp().isBlank()) {
                logger.warn("HNItem {} missing importTimestamp, marking as FAILED", entity.getId());
                entity.setStatus("FAILED");
                return entity;
            }

            // Business action: mark the HN item as persisted/stored.
            // Per rules: do not call entityService.update on the triggering entity.
            // Changing the entity state is sufficient; Cyoda will persist it.
            entity.setStatus("STORED");
            logger.info("HNItem {} marked as STORED", entity.getId());
        } catch (Exception ex) {
            logger.error("Error while processing HNItem {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}