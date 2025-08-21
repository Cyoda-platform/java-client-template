package com.java_template.application.processor;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

@Component
public class ScheduleJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ScheduleJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ScheduleJobProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(BatchJob.class)
            .validate(this::isValidEntity, "Invalid batch job")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(BatchJob job) {
        return job != null && job.isValid();
    }

    private BatchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<BatchJob> context) {
        BatchJob job = context.entity();
        try {
            // Determine if job should be scheduled or started immediately
            if (job.getScheduledFor() != null && !job.getScheduledFor().isBlank()) {
                try {
                    Instant scheduled = Instant.parse(job.getScheduledFor());
                    if (scheduled.isAfter(Instant.now())) {
                        job.setStatus("SCHEDULED");
                        logger.info("BatchJob {} scheduled for future execution at {}", job.getJobName(), job.getScheduledFor());
                        return job;
                    }
                } catch (DateTimeParseException ex) {
                    // invalid format should have been caught earlier; mark failed
                    job.setStatus("FAILED");
                    job.setErrorMessage("Invalid scheduledFor datetime");
                    return job;
                }
            }

            // Otherwise start immediately
            job.setStatus("IN_PROGRESS");
            job.setStartedAt(Instant.now().toString());
            logger.info("BatchJob {} starting immediately", job.getJobName());
            return job;
        } catch (Exception ex) {
            logger.error("Unexpected error during ScheduleJobProcessor", ex);
            job.setStatus("FAILED");
            job.setErrorMessage("Unexpected error during scheduling: " + ex.getMessage());
            return job;
        }
    }
}
