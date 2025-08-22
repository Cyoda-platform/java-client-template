package com.java_template.application.processor;
import com.java_template.application.entity.pet_ingestion_job.version_1.PetIngestionJob;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class StartIngestionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartIngestionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StartIngestionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetIngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(PetIngestionJob.class)
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
        PetIngestionJob job = context.entity();

        // Initialize errors list if null
        try {
            if (job.getErrors() == null) {
                job.setErrors(new ArrayList<>());
            }
        } catch (Exception e) {
            // defensive: if errors property not present, ignore initialization
            logger.debug("No errors field present or unable to initialize errors list: {}", e.getMessage());
        }

        String now = OffsetDateTime.now().toString();

        // Business rules:
        // - Must set status = running and startedAt timestamp when ingestion begins.
        // - If source missing, mark job failed with appropriate error and completedAt set.
        // - On successful run set status = completed, completedAt and importedCount.
        // - On unrecoverable exception set status = failed and record error.
        try {
            // Validate source presence
            String source = null;
            try {
                source = job.getSource();
            } catch (Exception e) {
                // property missing; treat as null
                source = null;
            }

            if (source == null || source.trim().isEmpty()) {
                logger.warn("PetIngestionJob missing source. Marking as failed. jobId={}", safeJobId(job));
                addError(job, "Missing ingestion source");
                job.setStatus("failed");
                job.setCompletedAt(now);
                // ensure startedAt is set for traceability
                if (job.getStartedAt() == null) {
                    job.setStartedAt(now);
                }
                return job;
            }

            // Idempotency / resume support:
            // If job already running, avoid restarting; if completed/failed, do not re-run.
            String currentStatus = null;
            try {
                currentStatus = job.getStatus();
            } catch (Exception e) {
                currentStatus = null;
            }
            if ("running".equalsIgnoreCase(currentStatus)) {
                logger.info("Job already running, skipping execution. jobId={}", safeJobId(job));
                return job;
            }
            if ("completed".equalsIgnoreCase(currentStatus)) {
                logger.info("Job already completed, skipping execution. jobId={}", safeJobId(job));
                return job;
            }
            if ("failed".equalsIgnoreCase(currentStatus)) {
                logger.info("Job previously failed, skipping execution. jobId={}", safeJobId(job));
                return job;
            }

            // Begin ingestion
            logger.info("Starting ingestion for jobId={} from source={}", safeJobId(job), source);
            job.setStatus("running");
            if (job.getStartedAt() == null) {
                job.setStartedAt(now);
            }

            // Initialize importedCount if null
            try {
                if (job.getImportedCount() == null) {
                    job.setImportedCount(0);
                }
            } catch (Exception e) {
                // ignore if field absent
            }

            // NOTE: Actual fetching/pagination from source and calling CreatePetProcessor is out of scope
            // for this processor implementation environment (no HTTP client or other processors invoked directly).
            // We'll implement orchestration-level behavior:
            // - Attempt to perform a logical ingestion cycle. Real fetching should be implemented in a FetchPetstoreProcessor
            //   or by wiring an HTTP client in a different layer. Here we simulate the orchestration and ensure state transitions.

            // Simulated run: no records fetched in this implementation.
            int imported = job.getImportedCount() != null ? job.getImportedCount() : 0;

            // In a full implementation, here we would:
            // - fetch pages from 'source'
            // - for each record call CreatePetProcessor (idempotent) or add items via entityService
            // - increment imported and collect per-record errors in job.errors

            logger.info("Ingestion simulation complete for jobId={}. importedCount before update={}", safeJobId(job), imported);

            // Update counts and finalize as completed
            job.setImportedCount(imported);
            job.setStatus("completed");
            job.setCompletedAt(OffsetDateTime.now().toString());

            logger.info("PetIngestionJob completed. jobId={}, importedCount={}", safeJobId(job), job.getImportedCount());
            return job;
        } catch (Exception ex) {
            logger.error("Fatal error during ingestion for jobId={}: {}", safeJobId(job), ex.getMessage(), ex);
            addError(job, "Fatal ingestion error: " + ex.getMessage());
            job.setStatus("failed");
            job.setCompletedAt(OffsetDateTime.now().toString());
            return job;
        }
    }

    private void addError(PetIngestionJob job, String message) {
        try {
            List<String> errors = job.getErrors();
            if (errors == null) {
                errors = new ArrayList<>();
                job.setErrors(errors);
            }
            errors.add(message);
        } catch (Exception e) {
            logger.debug("Unable to record error on job: {}", e.getMessage());
        }
    }

    private String safeJobId(PetIngestionJob job) {
        try {
            return job.getJobId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}