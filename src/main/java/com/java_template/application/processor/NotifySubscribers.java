package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.util.List;

@Component
public class NotifySubscribers implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifySubscribers.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifySubscribers(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing notifications for Job with request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(com.java_template.application.entity.job.version_1.Job.class)
                .validate(this::isValidEntity, "Invalid Job entity state during notification")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(com.java_template.application.entity.job.version_1.Job entity) {
        if (entity == null) return false;
        // Job must be in succeeded or failed state to notify subscribers
        String status = entity.getStatus();
        return status != null && ("succeeded".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status));
    }

    private com.java_template.application.entity.job.version_1.Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<com.java_template.application.entity.job.version_1.Job> context) {
        com.java_template.application.entity.job.version_1.Job job = context.entity();
        // Notify all active subscribers
        logger.info("Notifying active subscribers for Job: {}", job.getJobName());

        // Business logic to send notifications to all active subscribers
        // This is a placeholder for actual notification dispatch logic
        // Assume we have a method notifySubscribers() that handles this

        // TODO: Implement notification dispatch to active subscribers

        // After notifications, update job status to notified_subscribers
        job.setStatus("notified_subscribers");
        return job;
    }
}
