package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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
public class ExecuteJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ExecuteJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job execution for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null;
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();

        // Idempotent: if already COMPLETED or FAILED, skip
        String status = job.getStatus();
        if (status != null && ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status))) {
            logger.info("Job {} already in terminal state {} - skipping execution", job.getTechnicalId(), status);
            return job;
        }

        // Simulate job run: mark as COMPLETED
        job.setStatus("COMPLETED");
        if (job.getVersion() != null) job.setVersion(job.getVersion() + 1);
        logger.info("Job {} executed and marked COMPLETED", job.getTechnicalId());
        return job;
    }
}
