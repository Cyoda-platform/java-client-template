package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class PersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        // Ensure processedCount is not null
        if (job.getProcessedCount() == null) {
            job.setProcessedCount(0);
        }

        // Business logic:
        // - If some records were processed (processedCount > 0) mark job COMPLETED
        // - Otherwise mark job FAILED and set an error summary if missing
        // - Always set finishedAt when transitioning to a terminal state
        String currentStatus = job.getStatus();
        Integer processed = job.getProcessedCount();

        if (processed != null && processed > 0) {
            if (!"COMPLETED".equalsIgnoreCase(currentStatus)) {
                job.setStatus("COMPLETED");
                logger.info("IngestionJob {} marked as COMPLETED with {} processed items.", job.getJobId(), processed);
            }
            if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                job.setFinishedAt(Instant.now().toString());
            }
            // Clear error summary on success
            job.setErrorSummary(job.getErrorSummary() == null ? "" : job.getErrorSummary());
        } else {
            if (!"COMPLETED".equalsIgnoreCase(currentStatus)) { // avoid overwriting a successful state
                job.setStatus("FAILED");
                logger.warn("IngestionJob {} marked as FAILED. No items processed.", job.getJobId());
            }
            if (job.getErrorSummary() == null || job.getErrorSummary().isBlank()) {
                job.setErrorSummary("No records persisted during ingestion.");
            }
            if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                job.setFinishedAt(Instant.now().toString());
            }
        }

        // Note: Persisting of CoverPhoto entities (creation/updating) should be performed here
        // by interacting with entityService when records are available. This processor only
        // updates the job's state and metadata; actual CoverPhoto persistence will be handled
        // by other processors or earlier transformation steps which should call entityService.addItem(...)
        // as allowed by the workflow.

        return job;
    }
}