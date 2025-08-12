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
public class JobNotifySubscribersProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobNotifySubscribersProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public JobNotifySubscribersProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for notification request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job state for notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        // Valid states to notify subscribers: SUCCEEDED or FAILED
        return entity != null && ("SUCCEEDED".equalsIgnoreCase(entity.getStatus()) || "FAILED".equalsIgnoreCase(entity.getStatus()));
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();
        // Business logic: Notify all active subscribers about job completion
        // This could involve triggering notification events or persisting notification entities
        logger.info("Notifying subscribers for job: {}", entity.getJobName());

        // TODO: Implement actual notification logic here

        // Update job state to NOTIFIED_SUBSCRIBERS and set completedAt timestamp if not already set
        entity.setStatus("NOTIFIED_SUBSCRIBERS");
        if (entity.getCompletedAt() == null) {
            entity.setCompletedAt(java.time.Instant.now().toString());
        }

        return entity;
    }
}
