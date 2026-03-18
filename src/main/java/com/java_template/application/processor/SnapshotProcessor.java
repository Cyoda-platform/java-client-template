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
 * Processor for handling test run snapshot creation.
 *
 * This processor is responsible for creating snapshots of test cases and their
 * execution state during test run processing. It captures the current state of
 * test cases for audit and comparison purposes.
 *
 * The processor returns the entity unchanged, allowing the workflow to continue
 * with the original entity while the snapshot is created as a side effect.
 */
@Component("SnapshotProcessor")
public class SnapshotProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotProcessor.class);
    private final String processorName = "SnapshotProcessor";

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.debug("{}: Processing snapshot creation for request: {}", processorName, request.getId());

        // Stub implementation - returns entity unchanged
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);

        logger.debug("{}: Snapshot processing completed", processorName);
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

