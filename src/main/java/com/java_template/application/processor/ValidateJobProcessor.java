package com.java_template.application.processor;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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

import java.util.ArrayList;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid PetEnrichmentJob: missing required petSource")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Basic validation for PetEnrichmentJob before processing.
     * Requirement: petSource must be present (per functional requirements).
     */
    private boolean isValidEntity(PetEnrichmentJob entity) {
        if (entity == null) return false;
        String source = entity.getPetSource();
        return source != null && !source.isBlank();
    }

    /**
     * Implements business logic:
     * - Ensure errors list is initialized
     * - Ensure fetchedCount is normalized (non-null, non-negative)
     * - Set status to IN_PROGRESS to start the fetch workflow
     *
     * Note: Do NOT call entityService.update on this entity. Mutate fields and Cyoda will persist.
     */
    private PetEnrichmentJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetEnrichmentJob> context) {
        PetEnrichmentJob entity = context.entity();

        if (entity == null) return null;

        // Ensure errors list exists
        if (entity.getErrors() == null) {
            entity.setErrors(new ArrayList<>());
            logger.debug("Initialized errors list for PetEnrichmentJob {}", entity.getJobId());
        }

        // Normalize fetchedCount
        if (entity.getFetchedCount() == null) {
            entity.setFetchedCount(0);
            logger.debug("FetchedCount was null for job {}, set to 0", entity.getJobId());
        } else if (entity.getFetchedCount() < 0) {
            logger.warn("Job {} had negative fetchedCount ({}). Normalizing to 0.", entity.getJobId(), entity.getFetchedCount());
            entity.setFetchedCount(0);
            entity.getErrors().add("Normalized negative fetchedCount to 0");
        }

        // Set status to IN_PROGRESS if starting validation -> fetch should begin
        String status = entity.getStatus();
        if (status == null || status.isBlank() || "PENDING".equalsIgnoreCase(status)) {
            entity.setStatus("IN_PROGRESS");
            logger.info("Validated PetEnrichmentJob {} - setting status to IN_PROGRESS", entity.getJobId());
        } else {
            logger.debug("PetEnrichmentJob {} current status='{}'; leaving as-is", entity.getJobId(), status);
        }

        return entity;
    }
}