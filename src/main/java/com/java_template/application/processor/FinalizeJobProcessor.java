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
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(PetIngestionJob.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Determine final job outcome:
        // - If there are no errors and processedCount > 0 => COMPLETED
        // - Otherwise => FAILED
        boolean hasErrors = entity.getErrors() != null && !entity.getErrors().isEmpty();
        Integer processedCount = entity.getProcessedCount() == null ? 0 : entity.getProcessedCount();

        if (!hasErrors && processedCount > 0) {
            entity.setStatus("COMPLETED");
        } else {
            entity.setStatus("FAILED");
            // Ensure errors list exists and capture reason if not present
            if (entity.getErrors() == null) {
                entity.setErrors(new ArrayList<>());
            }
            if (processedCount == 0) {
                entity.getErrors().add("No items were processed by the job.");
            }
            if (!hasErrors && processedCount > 0) {
                // noop - already handled
            }
        }

        // Set completedAt timestamp if missing
        if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
            entity.setCompletedAt(Instant.now().toString());
        }

        logger.info("PetIngestionJob finalized. jobName={}, status={}, processedCount={}, errorCount={}",
                entity.getJobName(),
                entity.getStatus(),
                entity.getProcessedCount(),
                entity.getErrors() == null ? 0 : entity.getErrors().size()
        );

        return entity;
    }
}