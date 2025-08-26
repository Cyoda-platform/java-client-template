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
        // Only transition when job is currently in a startable state (e.g., PENDING).
        // Do not perform any add/update/delete operations on this Job entity via EntityService.
        // Cyoda will persist the modified entity state automatically.

        if (entity == null) {
            logger.warn("Received null Job entity in StartJobProcessor");
            return null;
        }

        try {
            String currentStatus = entity.getStatus();
            logger.info("Current status for job '{}': {}", entity.getJobName(), currentStatus);

            if (currentStatus == null || currentStatus.isBlank()) {
                // If status missing, consider it as PENDING and start
                logger.info("Status missing for job '{}', setting to IN_PROGRESS", entity.getJobName());
                entity.setStatus("IN_PROGRESS");
            } else {
                String normalized = currentStatus.trim().toUpperCase();
                switch (normalized) {
                    case "PENDING":
                    case "PENDING_APPROVAL":
                        // Acceptable to start
                        entity.setStatus("IN_PROGRESS");
                        logger.info("Job '{}' transitioned from '{}' to IN_PROGRESS", entity.getJobName(), currentStatus);
                        break;
                    case "IN_PROGRESS":
                        // Already started, no-op
                        logger.info("Job '{}' already IN_PROGRESS - no transition performed", entity.getJobName());
                        break;
                    case "COMPLETED":
                    case "FAILED":
                        // Terminal states: do not restart
                        logger.warn("Job '{}' is in terminal state '{}' - StartJobProcessor will not change state", entity.getJobName(), currentStatus);
                        break;
                    default:
                        // For any other state, be conservative and set to IN_PROGRESS only if it's a recognized start state
                        logger.info("Job '{}' in unrecognized state '{}' - setting to IN_PROGRESS to attempt start", entity.getJobName(), currentStatus);
                        entity.setStatus("IN_PROGRESS");
                        break;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to start job {}: {}", entity.getJobName(), e.getMessage(), e);
            // In case of unexpected error, set status to FAILED to reflect failure to start
            try {
                entity.setStatus("FAILED");
            } catch (Exception ex) {
                logger.warn("Unable to set FAILED status on job '{}'", entity.getJobName(), ex);
            }
        }

        return entity;
    }
}