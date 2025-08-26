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
public class StartJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job entity) {
        return entity != null && entity.isValid();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job entity = context.entity();

        // Business logic: mark the Job as IN_PROGRESS when started by this processor.
        // Do not perform any add/update/delete operations on this Job entity via EntityService.
        // Cyoda will persist the modified entity state automatically.
        try {
            logger.info("Starting job: {}", entity.getJobName());
            entity.setStatus("IN_PROGRESS");
        } catch (Exception e) {
            logger.error("Failed to start job {}: {}", entity != null ? entity.getJobName() : "unknown", e.getMessage(), e);
            // In case of unexpected error, set status to FAILED to reflect failure to start
            if (entity != null) {
                try {
                    entity.setStatus("FAILED");
                } catch (Exception ex) {
                    logger.warn("Unable to set FAILED status on job", ex);
                }
            }
        }

        return entity;
    }
}