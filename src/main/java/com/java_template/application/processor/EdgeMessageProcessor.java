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
 * Processor for handling edge message and attachment proxy operations.
 *
 * This processor is responsible for managing communication with edge message
 * services and handling attachment uploads/downloads through a proxy interface.
 * It facilitates the transfer of test artifacts and related attachments to
 * external edge message systems.
 *
 * The processor returns the entity unchanged, allowing the workflow to continue
 * with the original entity while attachment operations are performed as a side effect.
 */
@Component("EdgeMessageProcessor")
public class EdgeMessageProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(EdgeMessageProcessor.class);
    private final String processorName = "EdgeMessageProcessor";

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("{}: Processing edge message operations for request: {}", processorName, request.getId());

        // Stub implementation - returns entity unchanged
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);

        logger.debug("{}: Edge message processing completed", processorName);
        return response;
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return processorName.equals(opSpec.operationName());
    }

    /**
     * Gets the name of this processor.
     * @return the processor name
     */
    public String getName() {
        return processorName;
    }
}

