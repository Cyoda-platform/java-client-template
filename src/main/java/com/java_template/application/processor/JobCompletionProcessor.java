package com.java_template.application.processor;

import com.java_template.application.entity.dataextractionjob.version_1.DataExtractionJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class JobCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(JobCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public JobCompletionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DataExtractionJob completion for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DataExtractionJob.class)
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

    private boolean isValidEntity(DataExtractionJob entity) {
        return entity != null && entity.isValid();
    }

    private DataExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DataExtractionJob> context) {
        DataExtractionJob entity = context.entity();

        logger.info("Completing data extraction job: {}", entity.getJobName());

        // Set end time
        entity.setEndTime(LocalDateTime.now());

        // Calculate job duration
        Duration jobDuration = calculateJobDuration(entity);
        logger.info("Job duration: {} minutes", jobDuration.toMinutes());

        // Finalize counters and validate results
        finalizeJobCounters(entity);

        // Log job completion statistics
        logJobStatistics(entity, jobDuration);

        // Schedule next job run
        scheduleNextJobRun(entity);

        logger.info("Data extraction job completed: {}", entity.getJobName());
        return entity;
    }

    private Duration calculateJobDuration(DataExtractionJob job) {
        if (job.getStartTime() == null || job.getEndTime() == null) {
            logger.warn("Job start or end time is null, cannot calculate duration");
            return Duration.ZERO;
        }
        return Duration.between(job.getStartTime(), job.getEndTime());
    }

    private void finalizeJobCounters(DataExtractionJob job) {
        // Verify and finalize extraction counters
        int extracted = job.getRecordsExtracted() != null ? job.getRecordsExtracted() : 0;
        int processed = job.getRecordsProcessed() != null ? job.getRecordsProcessed() : 0;
        int failed = job.getRecordsFailed() != null ? job.getRecordsFailed() : 0;

        // Validate counters make sense
        if (processed + failed > extracted) {
            logger.warn("Processed + failed ({}) exceeds extracted ({}), adjusting counters", 
                       processed + failed, extracted);
            // Adjust processed count
            job.setRecordsProcessed(Math.max(0, extracted - failed));
        }

        // Calculate success rate
        double successRate = extracted > 0 ? (double) processed / extracted * 100 : 0;
        logger.info("Job success rate: {:.1f}%", successRate);

        // Update error log if there were failures
        if (failed > 0 && (job.getErrorLog() == null || job.getErrorLog().trim().isEmpty())) {
            job.setErrorLog(String.format("Job completed with %d failures out of %d records", failed, extracted));
        }
    }

    private void logJobStatistics(DataExtractionJob job, Duration duration) {
        logger.info("=== Job Completion Statistics ===");
        logger.info("Job Name: {}", job.getJobName());
        logger.info("Extraction Type: {}", job.getExtractionType());
        logger.info("Start Time: {}", job.getStartTime());
        logger.info("End Time: {}", job.getEndTime());
        logger.info("Duration: {} minutes", duration.toMinutes());
        logger.info("Records Extracted: {}", job.getRecordsExtracted());
        logger.info("Records Processed: {}", job.getRecordsProcessed());
        logger.info("Records Failed: {}", job.getRecordsFailed());
        
        if (job.getErrorLog() != null && !job.getErrorLog().trim().isEmpty()) {
            logger.info("Error Log: {}", job.getErrorLog());
        }
        
        logger.info("Next Scheduled Run: {}", job.getNextScheduledRun());
        logger.info("================================");
    }

    private void scheduleNextJobRun(DataExtractionJob completedJob) {
        try {
            // Create new DataExtractionJob for next execution
            DataExtractionJob nextJob = createNextJob(completedJob);
            
            // Save the next job
            entityService.save(nextJob);
            
            logger.info("Next job scheduled: {} for {}", nextJob.getJobName(), nextJob.getScheduledTime());
            
        } catch (Exception e) {
            logger.error("Failed to schedule next job run: {}", e.getMessage());
            // Update current job's error log
            String currentError = completedJob.getErrorLog() != null ? completedJob.getErrorLog() : "";
            completedJob.setErrorLog(currentError + "; Failed to schedule next run: " + e.getMessage());
        }
    }

    private DataExtractionJob createNextJob(DataExtractionJob currentJob) {
        DataExtractionJob nextJob = new DataExtractionJob();
        
        // Copy configuration from current job
        nextJob.setJobName(currentJob.getJobName());
        nextJob.setExtractionType(currentJob.getExtractionType());
        nextJob.setApiEndpoint(currentJob.getApiEndpoint());
        
        // Set scheduled time from current job's nextScheduledRun
        nextJob.setScheduledTime(currentJob.getNextScheduledRun());
        
        // Set next scheduled run (7 days after the new job's scheduled time)
        nextJob.setNextScheduledRun(currentJob.getNextScheduledRun().plusDays(7));
        
        // Initialize counters
        nextJob.setRecordsExtracted(0);
        nextJob.setRecordsProcessed(0);
        nextJob.setRecordsFailed(0);
        
        return nextJob;
    }
}
