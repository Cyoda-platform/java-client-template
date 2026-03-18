package com.java_template.application.processor;

import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub processor for handling atomic test failures
 * This processor handles individual test step failures and aggregates failure information
 */
@Component
public class AtomicFailureProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AtomicFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("AtomicFailureProcessor: Processing failure for request: {}", request.getId());

        // Stub implementation - would handle test step failures
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        
        logger.info("AtomicFailureProcessor: Failure processed successfully");
        return response;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

