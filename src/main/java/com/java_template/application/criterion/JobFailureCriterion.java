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

@Component
public class JobFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking job failure criteria for request: {}", request.getId());
        
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
        logger.info("Checking failure criteria for job: {}", entity.getJobName());

        // Check API connectivity issues
        if (hasApiConnectivityIssues(entity)) {
            logger.info("Job {} has API connectivity issues", entity.getJobName());
            return EvaluationOutcome.success();
        }

        // Check data extraction results
        if (hasDataExtractionFailures(entity)) {
            logger.info("Job {} has data extraction failures", entity.getJobName());
            return EvaluationOutcome.success();
        }

        // Check system resource issues
        if (hasSystemResourceIssues(entity)) {
            logger.info("Job {} has system resource issues", entity.getJobName());
            return EvaluationOutcome.success();
        }

        // Job can continue or recover
        logger.info("Job {} can continue or recover", entity.getJobName());
        return EvaluationOutcome.fail("Job can continue or recover", 
                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private boolean hasApiConnectivityIssues(DataExtractionJob job) {
        if (job.getErrorLog() != null) {
            String errorLog = job.getErrorLog().toLowerCase();
            
            // Check for Pet Store API unreachable
            if (errorLog.contains("connection refused") ||
                errorLog.contains("host unreachable") ||
                errorLog.contains("timeout") ||
                errorLog.contains("api unreachable")) {
                return true;
            }
            
            // Check for authentication failures
            if (errorLog.contains("authentication failed") ||
                errorLog.contains("unauthorized") ||
                errorLog.contains("invalid credentials")) {
                return true;
            }
            
            // Check for API rate limits exceeded
            if (errorLog.contains("rate limit") ||
                errorLog.contains("too many requests") ||
                errorLog.contains("quota exceeded")) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasDataExtractionFailures(DataExtractionJob job) {
        // Check if no products were successfully extracted
        Integer recordsExtracted = job.getRecordsExtracted();
        if (recordsExtracted == null || recordsExtracted == 0) {
            logger.warn("No records were extracted for job: {}", job.getJobName());
            return true;
        }
        
        // Check if more than 50% of API calls failed
        Integer recordsFailed = job.getRecordsFailed();
        if (recordsFailed != null && recordsExtracted != null) {
            double failureRate = (double) recordsFailed / (recordsExtracted + recordsFailed);
            if (failureRate > 0.5) {
                logger.warn("High failure rate ({:.1f}%) for job: {}", failureRate * 100, job.getJobName());
                return true;
            }
        }
        
        // Check for critical API endpoints unavailable
        if (job.getErrorLog() != null) {
            String errorLog = job.getErrorLog().toLowerCase();
            if (errorLog.contains("critical endpoint") ||
                errorLog.contains("all endpoints failed") ||
                errorLog.contains("api completely unavailable")) {
                return true;
            }
        }
        
        return false;
    }

    private boolean hasSystemResourceIssues(DataExtractionJob job) {
        if (job.getErrorLog() != null) {
            String errorLog = job.getErrorLog().toLowerCase();
            
            // Check for database connection failures
            if (errorLog.contains("database connection failed") ||
                errorLog.contains("db connection") ||
                errorLog.contains("database unavailable")) {
                return true;
            }
            
            // Check for insufficient disk space
            if (errorLog.contains("disk space") ||
                errorLog.contains("no space left") ||
                errorLog.contains("storage full")) {
                return true;
            }
            
            // Check for memory or processing limits
            if (errorLog.contains("out of memory") ||
                errorLog.contains("memory limit") ||
                errorLog.contains("cpu limit") ||
                errorLog.contains("processing limit")) {
                return true;
            }
        }
        
        return false;
    }
}
