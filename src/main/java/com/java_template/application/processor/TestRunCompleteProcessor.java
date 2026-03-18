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
 * Processor for handling test run completion.
 * 
 * This processor is responsible for finalizing test run execution and performing
 * completion-related operations. It handles cleanup, result aggregation, and
 * state transitions when a test run reaches its terminal state.
 * 
 * The processor returns the entity unchanged, allowing the workflow to continue
 * with the original entity while completion operations are performed as a side effect.
 */
@Component("TestRunCompleteProcessor")
public class TestRunCompleteProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(TestRunCompleteProcessor.class);
    private final String processorName = "TestRunCompleteProcessor";

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("{}: Processing test run completion for request: {}", processorName, request.getId());

        // Stub implementation - returns entity unchanged
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        
        logger.debug("{}: Test run completion processing completed", processorName);
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

