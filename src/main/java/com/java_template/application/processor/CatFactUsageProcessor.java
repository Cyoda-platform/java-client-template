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

import java.time.LocalDateTime;

/**
 * CatFactUsageProcessor - Marks cat fact as used in campaign
 * 
 * Input: CatFact entity in READY state
 * Purpose: Mark cat fact as used in campaign
 * Output: CatFact entity in USED state
 */
@Component
public class CatFactUsageProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactUsageProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactUsageProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact usage for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact for usage")
                .map(this::processCatFactUsage)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for usage
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return catFact != null && 
               catFact.isValid() && 
               "ready".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for cat fact usage
     */
    private EntityWithMetadata<CatFact> processCatFactUsage(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Marking cat fact as used: {} in state: {}", catFact.getFactId(), currentState);

        // Verify cat fact is in READY state
        if (!"ready".equalsIgnoreCase(currentState)) {
            logger.warn("CatFact {} is not in READY state, current state: {}", 
                       catFact.getFactId(), currentState);
            return entityWithMetadata;
        }

        // Update cat fact for usage
        catFact.setUsageCount(catFact.getUsageCount() + 1);
        catFact.setLastUsedDate(LocalDateTime.now());
        catFact.setIsUsed(true);
        
        logger.info("CatFact {} marked as used, usage count: {}", 
                   catFact.getFactId(), catFact.getUsageCount());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
