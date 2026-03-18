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
 * Processor for handling atomic test failure detection.
 *
 * This processor is responsible for detecting and processing individual atomic
 * test failures at the step level. It analyzes test execution results to identify
 * specific failure points and aggregates failure information for reporting.
 *
 * The processor returns the entity unchanged, allowing the workflow to continue
 * with the original entity while failure detection is performed as a side effect.
 */
@Component("AtomicFailureProcessor")
public class AtomicFailureProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(AtomicFailureProcessor.class);
    private final String processorName = "AtomicFailureProcessor";

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("{}: Processing atomic failure detection for request: {}", processorName, request.getId());

        // Stub implementation - returns entity unchanged
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);

        logger.debug("{}: Atomic failure detection completed", processorName);
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

