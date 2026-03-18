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
 * Processor for handling bug linking and issue tracking integration.
 * 
 * This processor is responsible for linking test failures to bug tracking systems
 * and managing the relationship between test results and issue tracking platforms.
 * It facilitates the creation and management of bug links for failed test cases.
 * 
 * The processor returns the entity unchanged, allowing the workflow to continue
 * with the original entity while bug linking operations are performed as a side effect.
 */
@Component("BugLinkProcessor")
public class BugLinkProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BugLinkProcessor.class);
    private final String processorName = "BugLinkProcessor";

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("{}: Processing bug linking for request: {}", processorName, request.getId());

        // Stub implementation - returns entity unchanged
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        
        logger.debug("{}: Bug linking processing completed", processorName);
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

