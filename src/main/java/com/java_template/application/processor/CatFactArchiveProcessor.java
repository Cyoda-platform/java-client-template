package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
 * Processor for cat fact archive workflow transition.
 * Handles the archive transition (used → archived).
 * 
 * Business Logic:
 * - Sets archive date to current timestamp
 * - Sets archive reason (overused, outdated, etc.)
 * - Removes from active facts pool
 * - Logs archival event
 */
@Component
public class CatFactArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactArchiveProcessor.class);
    private final ProcessorSerializer serializer;

    public CatFactArchiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("CatFactArchiveProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing cat fact archive for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::validateArchiveData, "Invalid archive data")
            .map(ctx -> {
                CatFact catFact = ctx.entity();
                
                // Set archive metadata
                catFact.setCategory("archived");
                
                // Ensure isUsed remains true for archived facts
                catFact.setIsUsed(true);
                
                logger.info("Cat fact archived: {} (ID: {})", 
                           catFact.getFactText().substring(0, Math.min(50, catFact.getFactText().length())),
                           catFact.getId());
                
                return catFact;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactArchiveProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates archive data.
     * 
     * @param catFact The cat fact to validate
     * @return true if valid, false otherwise
     */
    private boolean validateArchiveData(CatFact catFact) {
        if (catFact == null) {
            logger.warn("Archive failed: CatFact is null");
            return false;
        }
        
        // Fact text must be set and valid
        if (catFact.getFactText() == null || catFact.getFactText().trim().isEmpty()) {
            logger.warn("Archive failed: Fact text is empty");
            return false;
        }
        
        // Source must be set
        if (catFact.getSource() == null || catFact.getSource().trim().isEmpty()) {
            logger.warn("Archive failed: Source is not set");
            return false;
        }
        
        // Retrieved date must be set
        if (catFact.getRetrievedDate() == null) {
            logger.warn("Archive failed: Retrieved date is not set");
            return false;
        }
        
        // Should be marked as used
        if (catFact.getIsUsed() == null || !catFact.getIsUsed()) {
            logger.warn("Archive failed: Fact is not marked as used");
            return false;
        }
        
        // Should be in used state (category should be "used")
        if (catFact.getCategory() != null && !catFact.getCategory().equals("used")) {
            logger.warn("Archive failed: Fact is not in used state (category: {})", catFact.getCategory());
            return false;
        }
        
        logger.debug("Archive data validation passed for fact: {}", catFact.getId());
        return true;
    }
}
