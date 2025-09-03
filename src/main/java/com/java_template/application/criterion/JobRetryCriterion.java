package com.java_template.application.criterion;

import com.java_template.application.entity.dataextractionjob.version_1.DataExtractionJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class JobRetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobRetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking job retry criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(DataExtractionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataExtractionJob> context) {
        DataExtractionJob entity = context.entity();
        logger.info("Checking retry eligibility for job: {}", entity.getJobName());

        // Check retry conditions
        if (!areRetryConditionsMet(entity)) {
            return EvaluationOutcome.fail("Retry conditions are not met", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check business constraints
        if (!areBusinessConstraintsMet(entity)) {
            return EvaluationOutcome.fail("Business constraints for retry are not met", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Job is eligible for retry
        logger.info("Job {} is eligible for retry", entity.getJobName());
        return EvaluationOutcome.success();
    }

    private boolean areRetryConditionsMet(DataExtractionJob job) {
        // Check if job failure was due to temporary issues
        if (!wasFailureTemporary(job)) {
            logger.info("Job {} failure was not temporary, not eligible for retry", job.getJobName());
            return false;
        }

        // Check if Pet Store API is now available (simulated)
        if (!isPetStoreApiAvailable()) {
            logger.info("Pet Store API is still not available for retry");
            return false;
        }

        // Check if system resources are available
        if (!areSystemResourcesAvailable()) {
            logger.info("System resources are not available for retry");
            return false;
        }

        // Check retry count limits (simulated - in real implementation would track retries)
        if (hasExceededRetryLimit(job)) {
            logger.info("Job {} has exceeded retry limits", job.getJobName());
            return false;
        }

        return true;
    }

    private boolean areBusinessConstraintsMet(DataExtractionJob job) {
        // Check if job is still within scheduled execution window
        if (!isWithinExecutionWindow(job)) {
            logger.info("Job {} is outside execution window", job.getJobName());
            return false;
        }

        // Check if no newer job has been scheduled
        if (hasNewerJobBeenScheduled(job)) {
            logger.info("Newer job has been scheduled, not retrying {}", job.getJobName());
            return false;
        }

        // Check if data extraction is still needed
        if (!isDataExtractionStillNeeded(job)) {
            logger.info("Data extraction is no longer needed for job {}", job.getJobName());
            return false;
        }

        return true;
    }

    private boolean wasFailureTemporary(DataExtractionJob job) {
        if (job.getErrorLog() != null) {
            String errorLog = job.getErrorLog().toLowerCase();
            
            // Permanent failures that should not be retried
            if (errorLog.contains("authentication failed") ||
                errorLog.contains("invalid credentials") ||
                errorLog.contains("access denied") ||
                errorLog.contains("api key invalid")) {
                return false;
            }
            
            // Temporary failures that can be retried
            if (errorLog.contains("timeout") ||
                errorLog.contains("connection refused") ||
                errorLog.contains("rate limit") ||
                errorLog.contains("server unavailable") ||
                errorLog.contains("temporary")) {
                return true;
            }
        }
        
        // If no specific error information, assume it might be temporary
        return true;
    }

    private boolean isPetStoreApiAvailable() {
        // In a real implementation, this would check Pet Store API connectivity
        // For simulation, assume API is available 80% of the time
        return System.currentTimeMillis() % 5 != 0;
    }

    private boolean areSystemResourcesAvailable() {
        // In a real implementation, this would check:
        // - Database connectivity
        // - Available disk space
        // - Memory usage
        // - CPU load
        
        // For simulation, assume resources are available 90% of the time
        return System.currentTimeMillis() % 10 != 0;
    }

    private boolean hasExceededRetryLimit(DataExtractionJob job) {
        // In a real implementation, this would track retry attempts
        // For simulation, assume max 3 retries and check based on job characteristics
        
        // Use job name hash to simulate retry count
        int simulatedRetryCount = Math.abs(job.getJobName().hashCode()) % 5;
        return simulatedRetryCount >= 3;
    }

    private boolean isWithinExecutionWindow(DataExtractionJob job) {
        if (job.getScheduledTime() == null) {
            return true; // No time constraint
        }
        
        // Check if job is still within reasonable execution window (e.g., 24 hours)
        long hoursSinceScheduled = ChronoUnit.HOURS.between(job.getScheduledTime(), LocalDateTime.now());
        return hoursSinceScheduled <= 24;
    }

    private boolean hasNewerJobBeenScheduled(DataExtractionJob job) {
        // In a real implementation, this would query for newer jobs of the same type
        // For simulation, assume newer jobs exist for old scheduled times
        
        if (job.getScheduledTime() == null) {
            return false;
        }
        
        long daysSinceScheduled = ChronoUnit.DAYS.between(job.getScheduledTime(), LocalDateTime.now());
        return daysSinceScheduled > 7; // Assume weekly jobs, so older than a week means newer job exists
    }

    private boolean isDataExtractionStillNeeded(DataExtractionJob job) {
        // In a real implementation, this would check if:
        // - Data has already been extracted by another job
        // - Business requirements have changed
        // - System is in maintenance mode
        
        // For simulation, assume data extraction is always needed unless job is very old
        if (job.getScheduledTime() == null) {
            return true;
        }
        
        long daysSinceScheduled = ChronoUnit.DAYS.between(job.getScheduledTime(), LocalDateTime.now());
        return daysSinceScheduled <= 30; // Data extraction needed if job is less than 30 days old
    }
}
