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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetEnrichmentJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business logic:
        // - If there are recorded errors, mark job as FAILED.
        // - If no errors, ensure fetchedCount is non-null and mark job as NOTIFIED (finalized).
        // - Preserve other fields. The triggering entity will be persisted automatically by Cyoda.

        if (entity == null) {
            logger.warn("PetEnrichmentJob entity is null in FinalizeJobProcessor");
            return entity;
        }

        if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
            logger.info("Finalizing job {}: errors present (count={}), marking as FAILED", entity.getJobId(), entity.getErrors().size());
            entity.setStatus("FAILED");
        } else {
            // Ensure fetchedCount is non-null and non-negative
            if (entity.getFetchedCount() == null) {
                entity.setFetchedCount(0);
            } else if (entity.getFetchedCount() < 0) {
                logger.warn("Job {} had negative fetchedCount ({}). Normalizing to 0.", entity.getJobId(), entity.getFetchedCount());
                entity.setFetchedCount(0);
            }

            // If job is not already in COMPLETED state, ensure it is considered completed.
            String currentStatus = entity.getStatus();
            if (currentStatus == null || currentStatus.isBlank() || !"COMPLETED".equalsIgnoreCase(currentStatus)) {
                logger.debug("Job {} status '{}' is not COMPLETED; setting to COMPLETED before notify.", entity.getJobId(), currentStatus);
                entity.setStatus("COMPLETED");
            }

            // Finalize/notify step: mark as NOTIFIED to indicate finalization has occurred.
            logger.info("Finalizing job {}: marking as NOTIFIED (fetchedCount={})", entity.getJobId(), entity.getFetchedCount());
            entity.setStatus("NOTIFIED");
        }

        return entity;
    }
}