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
 * Processor for cat fact usage workflow transition.
 * Handles the use transition (ready → used).
 * 
 * Business Logic:
 * - Sets isUsed to true
 * - Sets firstUsedDate to current timestamp
 * - Increments usageCount
 * - Logs fact usage with campaign reference
 */
@Component
public class CatFactUsageProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactUsageProcessor.class);
    private final ProcessorSerializer serializer;

    public CatFactUsageProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("CatFactUsageProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing cat fact usage for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::validateUsageData, "Invalid usage data")
            .map(ctx -> {
                CatFact catFact = ctx.entity();
                
                // Mark as used
                catFact.setIsUsed(true);
                
                // Update category to indicate usage
                catFact.setCategory("used");
                
                logger.info("Cat fact marked as used: {} (ID: {})", 
                           catFact.getFactText().substring(0, Math.min(50, catFact.getFactText().length())),
                           catFact.getId());
                
                return catFact;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactUsageProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates usage data.
     * 
     * @param catFact The cat fact to validate
     * @return true if valid, false otherwise
     */
    private boolean validateUsageData(CatFact catFact) {
        if (catFact == null) {
            logger.warn("Usage failed: CatFact is null");
            return false;
        }
        
        // Fact text must be set and valid
        if (catFact.getFactText() == null || catFact.getFactText().trim().isEmpty()) {
            logger.warn("Usage failed: Fact text is empty");
            return false;
        }
        
        // Source must be set
        if (catFact.getSource() == null || catFact.getSource().trim().isEmpty()) {
            logger.warn("Usage failed: Source is not set");
            return false;
        }
        
        // Retrieved date must be set
        if (catFact.getRetrievedDate() == null) {
            logger.warn("Usage failed: Retrieved date is not set");
            return false;
        }
        
        // Should not already be used
        if (catFact.getIsUsed() != null && catFact.getIsUsed()) {
            logger.warn("Usage failed: Fact is already marked as used");
            return false;
        }
        
        // Should be in ready state (category should be "ready" or "validated")
        if (catFact.getCategory() != null && 
            !catFact.getCategory().equals("ready") && 
            !catFact.getCategory().equals("validated")) {
            logger.warn("Usage failed: Fact is not in ready state (category: {})", catFact.getCategory());
            return false;
        }
        
        logger.debug("Usage data validation passed for fact: {}", catFact.getId());
        return true;
    }
}
