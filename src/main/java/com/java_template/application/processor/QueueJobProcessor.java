package com.java_template.application.processor;

import com.java_template.application.entity.transformjob.version_1.TransformJob;
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
public class QueueJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QueueJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public QueueJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing TransformJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(TransformJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformJob entity) {
        return entity != null && entity.isValid();
    }

    private TransformJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformJob> context) {
        TransformJob entity = context.entity();
        if (entity == null) {
            logger.warn("TransformJob entity is null in execution context");
            return null;
        }

        String currentStatus = entity.getStatus();
        logger.info("Current job status for id {}: {}", entity.getId(), currentStatus);

        // Business rule:
        // QueueJobProcessor should move newly created PENDING jobs into QUEUED state.
        // If status is null/blank or explicitly PENDING -> set to QUEUED.
        if (currentStatus == null || currentStatus.isBlank() || "PENDING".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("QUEUED");
            // Ensure numeric fields are initialized sensibly
            if (entity.getResultCount() == null) {
                entity.setResultCount(0);
            }
            // Clear previous error messages when queuing
            entity.setErrorMessage(null);
            // outputLocation should remain as-is; StartJobProcessor / subsequent processors will set startedAt/outputLocation/completedAt
            logger.info("TransformJob id {} moved to QUEUED", entity.getId());
        } else {
            logger.info("TransformJob id {} not queued because current status is '{}'", entity.getId(), currentStatus);
        }

        return entity;
    }
}