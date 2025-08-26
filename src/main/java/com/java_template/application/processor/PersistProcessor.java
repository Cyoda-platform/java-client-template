package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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

        return serializer.withRequest(request)
            .toEntity(IngestionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        if (job == null) {
            logger.warn("Received null IngestionJob in PersistProcessor");
            return null;
        }

        logger.info("PersistProcessor executing business logic for jobId={}, currentStatus={}, processedCount={}",
                job.getJobId(), job.getStatus(), job.getProcessedCount());

        // Ensure finishedAt is set when we complete/fail the job
        String now = Instant.now().toString();

        // Determine final status:
        // - If processedCount > 0 and no error summary -> COMPLETED
        // - Otherwise -> FAILED (and set an error summary if none provided)
        Integer processed = job.getProcessedCount();
        String errorSummary = job.getErrorSummary();

        boolean hasProcessedRecords = processed != null && processed > 0;
        boolean hasErrors = errorSummary != null && !errorSummary.isBlank();

        if (hasProcessedRecords && !hasErrors) {
            job.setStatus("COMPLETED");
            if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                job.setFinishedAt(now);
            }
            logger.info("IngestionJob {} marked COMPLETED, processedCount={}", job.getJobId(), processed);
        } else {
            job.setStatus("FAILED");
            if (errorSummary == null || errorSummary.isBlank()) {
                job.setErrorSummary(hasProcessedRecords ? "Processing finished with unknown errors" : "No records were processed");
            }
            if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
                job.setFinishedAt(now);
            }
            logger.warn("IngestionJob {} marked FAILED, processedCount={}, errorSummary={}", job.getJobId(), processed, job.getErrorSummary());
        }

        // Update processedCount to 0 if missing to avoid nulls downstream
        if (job.getProcessedCount() == null) {
            job.setProcessedCount(0);
        }

        // Cyoda will persist the updated entity state automatically.
        return job;
    }
}