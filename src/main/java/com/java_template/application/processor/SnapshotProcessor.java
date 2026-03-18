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
 * Stub processor for creating test run snapshots
 * This processor handles the creation of test run snapshots from test cases
 */
@Component
public class SnapshotProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotProcessor.class);
    private final String className = this.getClass().getSimpleName();

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("SnapshotProcessor: Creating snapshot for request: {}", request.getId());

        // Stub implementation - would create snapshot of test cases for a test run
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        
        logger.info("SnapshotProcessor: Snapshot created successfully");
        return response;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}

