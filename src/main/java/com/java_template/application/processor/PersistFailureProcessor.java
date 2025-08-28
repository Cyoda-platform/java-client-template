package com.java_template.application.processor;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;

@Component
public class PersistFailureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistFailureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistFailureProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetIngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob entity = context.entity();

        // Mark job as FAILED and record timestamp and error entry.
        logger.info("PersistFailureProcessor updating job '{}' status to FAILED", entity.getJobName());

        // Ensure processedCount is not null
        if (entity.getProcessedCount() == null) {
            entity.setProcessedCount(0);
        }

        // Ensure errors list is initialized
        if (entity.getErrors() == null) {
            entity.setErrors(new ArrayList<>());
        }

        // Append failure detail with timestamp and processor info
        String errDetail = String.format("Job marked FAILED by %s at %s", className, Instant.now().toString());
        entity.getErrors().add(errDetail);

        // Update status and completion time
        entity.setStatus("FAILED");
        entity.setCompletedAt(Instant.now().toString());

        logger.warn("PetIngestionJob '{}' failed: {}", entity.getJobName(), errDetail);

        return entity;
    }
}