package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.util.UUID;

/**
 * CatFactArchivalProcessor - Handles cat fact archival after being sent
 * Transition: SENT → ARCHIVED
 * 
 * Business Logic:
 * 1. Validate cat fact exists and is in SENT state
 * 2. Set isUsed to true
 * 3. Log archival timestamp
 * 4. Clean up any temporary data
 */
@Component
public class CatFactArchivalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactArchivalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactArchivalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact archival for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        
        return entity != null && entity.isValid() && technicalId != null && "SENT".equals(currentState);
    }

    /**
     * Main business logic processing method for cat fact archival
     */
    private EntityWithMetadata<CatFact> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing cat fact archival: {} in state: {}", entity.getId(), currentState);

        // Set isUsed to true
        entity.setIsUsed(true);

        logger.info("CatFact {} archived successfully", entity.getId());

        return entityWithMetadata;
    }
}
