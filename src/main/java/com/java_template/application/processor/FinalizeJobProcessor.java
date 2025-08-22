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

@Component
public class FinalizeJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public FinalizeJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetEnrichmentJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(PetEnrichmentJob entity) {
        return entity != null && entity.isValid();
    }

    private PetEnrichmentJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<PetEnrichmentJob> context) {
        PetEnrichmentJob entity = context.entity();

        // Ensure errors list exists
        if (entity.getErrors() == null) {
            entity.setErrors(new java.util.ArrayList<>());
        }

        // If there are errors, mark the job as FAILED and exit processing
        if (!entity.getErrors().isEmpty()) {
            logger.info("Finalizing job {}: errors present (count={}), marking as FAILED", entity.getJobId(), entity.getErrors().size());
            entity.setStatus("FAILED");
            return entity;
        }

        // Normalize fetchedCount
        if (entity.getFetchedCount() == null) {
            entity.setFetchedCount(0);
        } else if (entity.getFetchedCount() < 0) {
            logger.warn("Job {} had negative fetchedCount ({}). Normalizing to 0.", entity.getJobId(), entity.getFetchedCount());
            entity.setFetchedCount(0);
        }

        // Ensure job is marked as COMPLETED before notifying
        String currentStatus = entity.getStatus();
        if (currentStatus == null || !currentStatus.equalsIgnoreCase("COMPLETED")) {
            logger.debug("Job {} status '{}' is not COMPLETED; setting to COMPLETED before notify.", entity.getJobId(), currentStatus);
            entity.setStatus("COMPLETED");
        }

        // Final step: mark job as NOTIFIED
        logger.info("Finalizing job {}: marking as NOTIFIED (fetchedCount={})", entity.getJobId(), entity.getFetchedCount());
        entity.setStatus("NOTIFIED");

        return entity;
    }
}