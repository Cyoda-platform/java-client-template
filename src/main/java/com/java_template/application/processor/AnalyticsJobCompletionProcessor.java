package com.java_template.application.processor;

import com.java_template.application.entity.analyticsjob.version_1.AnalyticsJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.dto.EntityResponse;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.Duration;

@Component
public class AnalyticsJobCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsJobCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AnalyticsJobCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AnalyticsJob completion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AnalyticsJob.class)
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

    private boolean isValidEntity(AnalyticsJob entity) {
        return entity != null && entity.isValid();
    }

    private AnalyticsJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AnalyticsJob> context) {
        AnalyticsJob job = context.entity();

        try {
            job.setCompletedAt(LocalDateTime.now());

            // Log completion metrics
            Duration duration = Duration.between(job.getStartedAt(), job.getCompletedAt());
            logger.info("Job completed: {} (Duration: {} seconds, Books: {}, Reports: {})",
                job.getJobId(),
                duration.getSeconds(),
                job.getBooksProcessed(),
                job.getReportsGenerated());

            // Trigger next scheduled job if exists and it's time
            triggerNextJobIfReady(job);

        } catch (Exception e) {
            logger.error("Failed to complete job {}: {}", job.getJobId(), e.getMessage(), e);
            throw new RuntimeException("Job completion failed: " + e.getMessage(), e);
        }

        return job;
    }

    private void triggerNextJobIfReady(AnalyticsJob currentJob) {
        if (currentJob.getNextJobId() == null || currentJob.getNextJobId().trim().isEmpty()) {
            logger.info("No next job scheduled for: {}", currentJob.getJobId());
            return;
        }

        try {
            // Find the next job by business ID
            EntityResponse<AnalyticsJob> nextJobResponse = entityService.findByBusinessId(
                AnalyticsJob.class, currentJob.getNextJobId(), "jobId");

            if (nextJobResponse == null) {
                logger.warn("Next job not found: {}", currentJob.getNextJobId());
                return;
            }

            AnalyticsJob nextJob = nextJobResponse.getData();
            LocalDateTime now = LocalDateTime.now();

            // Check if it's time to start the next job
            if (nextJob.getScheduledFor().isBefore(now) || nextJob.getScheduledFor().isEqual(now)) {
                logger.info("Triggering next job: {}", nextJob.getJobId());
                
                // Update the next job to trigger execution
                // In a real implementation, this would trigger the "start_job_execution" transition
                // For now, we'll just log the action
                logger.info("Would trigger transition 'start_job_execution' for job: {}", nextJob.getJobId());
                
                // Note: Actual transition triggering would require workflow API calls
                // entityService.update(nextJobResponse.getMetadata().getId(), nextJob, "start_job_execution");
                
            } else {
                logger.info("Next job {} is scheduled for {}, not ready to start yet", 
                    nextJob.getJobId(), nextJob.getScheduledFor());
            }

        } catch (Exception e) {
            logger.error("Failed to trigger next job {}: {}", currentJob.getNextJobId(), e.getMessage(), e);
            // Don't throw exception here as the current job completion should still succeed
        }
    }
}
