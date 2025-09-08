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
 * CatFactReuseProcessor - Handles reuse of cat fact in new campaign
 * 
 * Input: CatFact entity in USED state
 * Purpose: Handle reuse of cat fact in new campaign
 * Output: CatFact entity remains in USED state
 */
@Component
public class CatFactReuseProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactReuseProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactReuseProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact reuse for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact for reuse")
                .map(this::processCatFactReuse)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper for reuse
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
     * Main business logic for cat fact reuse
     */
    private EntityWithMetadata<CatFact> processCatFactReuse(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact catFact = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Reusing cat fact: {} in state: {}", catFact.getFactId(), currentState);

        // Verify cat fact is in USED state
        if (!"used".equalsIgnoreCase(currentState)) {
            logger.warn("CatFact {} is not in USED state, current state: {}", 
                       catFact.getFactId(), currentState);
            return entityWithMetadata;
        }

        // Update cat fact for reuse
        catFact.setUsageCount(catFact.getUsageCount() + 1);
        catFact.setLastUsedDate(LocalDateTime.now());
        
        logger.info("CatFact {} reused successfully, usage count: {}", 
                   catFact.getFactId(), catFact.getUsageCount());

        // Return updated entity (state remains USED)
        return entityWithMetadata;
    }
}
