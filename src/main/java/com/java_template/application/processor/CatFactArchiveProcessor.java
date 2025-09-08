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

/**
 * CatFactArchiveProcessor - Archives overused cat facts
 * 
 * Input: CatFact entity in USED state
 * Purpose: Archive overused cat facts
 * Output: CatFact entity in ARCHIVED state
 */
@Component
public class CatFactArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactArchiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactArchiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact archive for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact for archive")
                .map(this::processCatFactArchive)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for archive
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();
        
        return catFact != null && 
               catFact.isValid() && 
               "used".equalsIgnoreCase(currentState) &&
               entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic for cat fact archiving
     */
    private EntityWithMetadata<CatFact> processCatFactArchive(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Archiving cat fact: {} in state: {}", catFact.getFactId(), currentState);

        // Verify cat fact is in USED state
        if (!"used".equalsIgnoreCase(currentState)) {
            logger.warn("CatFact {} is not in USED state, current state: {}", 
                       catFact.getFactId(), currentState);
            return entityWithMetadata;
        }

        // Verify usage count exceeds archive threshold
        if (catFact.getUsageCount() < 10) {
            logger.warn("CatFact {} usage count {} is below archive threshold", 
                       catFact.getFactId(), catFact.getUsageCount());
            return entityWithMetadata;
        }

        logger.info("CatFact {} archived successfully, final usage count: {}", 
                   catFact.getFactId(), catFact.getUsageCount());

        // Return updated entity (state transition will be handled by workflow)
        return entityWithMetadata;
    }
}
