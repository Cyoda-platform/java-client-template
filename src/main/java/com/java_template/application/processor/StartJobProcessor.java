package com.java_template.application.processor;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state for StartJobProcessor")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Accept job entities that are non-null and in PENDING state and have required metadata to start.
     * We intentionally avoid calling entity.isValid() because the lifecycle requires startedAt to be set by this processor.
     */
    private boolean isValidEntity(PetIngestionJob entity) {
        if (entity == null) return false;
        // Must be in PENDING to start the job
        String status = entity.getStatus();
        if (status == null) return false;
        if (!"PENDING".equalsIgnoreCase(status.trim())) return false;
        // Basic required metadata: jobName and sourceUrl must be present to start
        if (entity.getJobName() == null || entity.getJobName().isBlank()) return false;
        if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) return false;
        return true;
    }

    private PetIngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetIngestionJob> context) {
        PetIngestionJob entity = context.entity();

        // Initialize or reset processing fields as we start the job
        try {
            // Set start time if not set
            if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                entity.setStartedAt(Instant.now().toString());
            }

            // Initialize processedCount if null
            if (entity.getProcessedCount() == null) {
                entity.setProcessedCount(0);
            }

            // Ensure errors list exists
            if (entity.getErrors() == null) {
                entity.setErrors(new ArrayList<>());
            }

            // Transition to VALIDATING state to trigger subsequent criteria/processors
            entity.setStatus("VALIDATING");

            logger.info("Started PetIngestionJob '{}' - sourceUrl={}, startedAt={}", entity.getJobName(), entity.getSourceUrl(), entity.getStartedAt());
        } catch (Exception ex) {
            logger.error("Error initializing PetIngestionJob: {}", ex.getMessage(), ex);
            // mark failed with error info
            List<String> errs = entity.getErrors();
            if (errs == null) {
                errs = new ArrayList<>();
                entity.setErrors(errs);
            }
            errs.add("StartJobProcessor failure: " + ex.getMessage());
            entity.setStatus("FAILED");
            if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                entity.setCompletedAt(Instant.now().toString());
            }
        }

        return entity;
    }
}