package com.java_template.application.processor;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class ImportFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ImportFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob fetch for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob job) {
        return job != null && job.getJobId() != null && !job.getJobId().isEmpty();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            String status = job.getStatus();
            if (status != null && !"PENDING".equals(status)) {
                logger.info("ImportJob {} is in status {} - skipping fetch", job.getJobId(), status);
                return job;
            }

            job.setStatus("IN_PROGRESS");
            try {
                job.setStartedAt(Instant.now().toString());
            } catch (Throwable ignore) {
            }

            // In a real implementation we'd perform HTTP calls to job.getSourceUrl() and handle results.
            // For the prototype we mark it as PROCESSING so downstream processors can act.
            job.setStatus("PROCESSING");
            logger.info("ImportJob {} moved to PROCESSING (fetch simulated)", job.getJobId());
            return job;
        } catch (Exception e) {
            logger.error("Unhandled error while fetching import job {}", job == null ? "<null>" : job.getJobId(), e);
            if (job != null) {
                job.setStatus("FAILED");
                try {
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(Instant.now().toString());
                } catch (Throwable ignore) {
                }
            }
            return job;
        }
    }
}
