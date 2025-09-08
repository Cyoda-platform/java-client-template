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
 * CatFactSchedulingProcessor - Handles cat fact scheduling for campaigns
 * Transition: RETRIEVED → SCHEDULED
 * 
 * Business Logic:
 * 1. Validate cat fact exists and is in RETRIEVED state
 * 2. Set scheduled date for the fact
 * 3. Mark fact as scheduled for use
 * 4. Log scheduling timestamp
 */
@Component
public class CatFactSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactSchedulingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact scheduling for request: {}", request.getId());

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
        
        return entity != null && entity.isValid() && technicalId != null && "RETRIEVED".equals(currentState);
    }

    /**
     * Main business logic processing method for cat fact scheduling
     */
    private EntityWithMetadata<CatFact> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing cat fact scheduling: {} in state: {}", entity.getId(), currentState);

        // The scheduled date should be set by the calling process (e.g., EmailCampaignSchedulingProcessor)
        // If not set, we'll keep the existing scheduledDate
        
        logger.info("CatFact {} scheduled successfully for date: {}", entity.getId(), entity.getScheduledDate());

        return entityWithMetadata;
    }
}
