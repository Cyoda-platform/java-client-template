package com.java_template.application.processor;

import com.java_template.application.entity.job.version_1.Job;
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
public class IngestJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IngestJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IngestJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Job for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Job.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Job job) {
        return job != null && job.getTechnicalId() != null && !job.getTechnicalId().isEmpty();
    }

    private Job processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Job> context) {
        Job job = context.entity();
        // Basic ingestion orchestration implementation. This processor focuses on state transitions and counters.
        try {
            logger.info("Starting ingest for job: {}", job.getTechnicalId());
            job.setStartTime(Instant.now().toString());
            job.setStatus("INGESTING");

            // Validate sourceUrl
            if (job.getSourceUrl() == null || job.getSourceUrl().isEmpty()) {
                job.setStatus("FAILED");
                job.setErrorDetails("Missing sourceUrl");
                job.setEndTime(Instant.now().toString());
                logger.error("Job {} failed because sourceUrl is missing", job.getTechnicalId());
                return job;
            }

            // Simulate fetch and processing steps conservatively: we do not call external HTTP here
            job.setStatus("FETCHING");
            // In a real implementation: perform HTTP fetch with retries, parse records and populate fetchedRecordCount
            job.setFetchedRecordCount(job.getFetchedRecordCount() == null ? 0 : job.getFetchedRecordCount());

            job.setStatus("PROCESSING_RECORDS");
            // In a real implementation: iterate fetched records and persist laureates honoring dedupeStrategy
            // For prototype, we assume zero records processed if none provided and mark success
            Integer fetched = job.getFetchedRecordCount() == null ? 0 : job.getFetchedRecordCount();
            job.setPersistedRecordCount(job.getPersistedRecordCount() == null ? 0 : job.getPersistedRecordCount());
            job.setSucceededCount(job.getSucceededCount() == null ? 0 : job.getSucceededCount());
            job.setFailedCount(job.getFailedCount() == null ? 0 : job.getFailedCount());

            // Decide final job status using counters
            if (job.getErrorDetails() != null && !job.getErrorDetails().isEmpty()) {
                job.setStatus("FAILED");
            } else if (job.getFailedCount() != null && job.getFailedCount() > 0) {
                job.setStatus("PARTIAL_FAILURE");
            } else {
                // If fetch produced at least 0 records and no fatal errors
                job.setStatus("SUCCEEDED");
            }

        } catch (Exception e) {
            logger.error("Fatal error while processing job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorDetails(e.toString());
        } finally {
            job.setEndTime(Instant.now().toString());
            logger.info("Job {} finished with status {}", job.getTechnicalId(), job.getStatus());
        }

        return job;
    }
}
