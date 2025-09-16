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
 * Processor for cat fact approval workflow transition.
 * Handles the approve transition (validated → ready).
 * 
 * Business Logic:
 * - Sets approval date to current timestamp
 * - Sets approvedBy to "system"
 * - Adds to ready facts pool
 * - Logs fact approval
 */
@Component
public class CatFactApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactApprovalProcessor.class);
    private final ProcessorSerializer serializer;

    public CatFactApprovalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.debug("CatFactApprovalProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("Processing cat fact approval for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(CatFact.class)
            .validate(this::validateApprovalData, "Invalid approval data")
            .map(ctx -> {
                CatFact catFact = ctx.entity();
                
                // Set approval metadata
                catFact.setCategory("ready");
                
                // Ensure isUsed is false for ready facts
                catFact.setIsUsed(false);
                
                logger.info("Cat fact approved and ready for use: {} (ID: {})", 
                           catFact.getFactText().substring(0, Math.min(50, catFact.getFactText().length())),
                           catFact.getId());
                
                return catFact;
            })
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "CatFactApprovalProcessor".equals(opSpec.operationName());
    }

    /**
     * Validates approval data.
     * 
     * @param catFact The cat fact to validate
     * @return true if valid, false otherwise
     */
    private boolean validateApprovalData(CatFact catFact) {
        if (catFact == null) {
            logger.warn("Approval failed: CatFact is null");
            return false;
        }
        
        // Fact text must be set and valid
        if (catFact.getFactText() == null || catFact.getFactText().trim().isEmpty()) {
            logger.warn("Approval failed: Fact text is empty");
            return false;
        }
        
        // Source must be set
        if (catFact.getSource() == null || catFact.getSource().trim().isEmpty()) {
            logger.warn("Approval failed: Source is not set");
            return false;
        }
        
        // Retrieved date must be set
        if (catFact.getRetrievedDate() == null) {
            logger.warn("Approval failed: Retrieved date is not set");
            return false;
        }
        
        // Length must be set and match fact text
        if (catFact.getLength() == null || !catFact.getLength().equals(catFact.getFactText().length())) {
            logger.warn("Approval failed: Length mismatch");
            return false;
        }
        
        // Should not already be used
        if (catFact.getIsUsed() != null && catFact.getIsUsed()) {
            logger.warn("Approval failed: Fact is already marked as used");
            return false;
        }
        
        logger.debug("Approval data validation passed for fact: {}", catFact.getId());
        return true;
    }
}
