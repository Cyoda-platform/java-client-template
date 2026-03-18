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
 * Stub processor for EdgeMessage client integration
 * This processor handles communication with edge message services for attachment uploads
 */
@Component
public class EdgeMessageProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EdgeMessageProcessor.class);
    private final String className = this.getClass().getSimpleName();

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("EdgeMessageProcessor: Processing message for request: {}", request.getId());

        // Stub implementation - would call EdgeMessage client for attachment uploads
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        
        logger.info("EdgeMessageProcessor: Message processed successfully");
        return response;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

