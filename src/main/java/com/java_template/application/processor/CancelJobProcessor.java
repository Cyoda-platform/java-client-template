package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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

@Component
public class CancelJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CancelJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CancelJobProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid batch job for cancellation")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob job) {
        return job != null && job.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob job = context.entity();
        try {
            job.setStatus("CANCELLED");
            logger.info("Cancelled BatchJob {}", job.getJobName());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error during CancelJobProcessor", ex);
            job.setStatus("FAILED");
            job.setErrorMessage("Cancel error: " + ex.getMessage());
            return job;
        }
    }
}
