package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null) return false;
        // Must use exact criterion name (case-sensitive)
        String operationName = modelSpec.operationName();
        return className.equals(operationName);
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("PersistSuccessCriterion: received null Job entity");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Helper accessors use reflection to avoid compile-time dependency on specific Job getters.
         String jobId = getStringProperty(job, "getJobId");
         if (jobId == null || jobId.isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sourceEndpoint = getStringProperty(job, "getSourceEndpoint");
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         String status = getStringProperty(job, "getStatus");
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         status = status.trim();

         // Only terminal states are considered a persisted outcome for this criterion
         if ("COMPLETED".equalsIgnoreCase(status)) {
             // COMPLETED must have result summary and last run timestamp
             String resultSummary = getStringProperty(job, "getResultSummary");
             if (resultSummary == null || resultSummary.isBlank()) {
                 return EvaluationOutcome.fail("resultSummary must be provided for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String lastRunAt = getStringProperty(job, "getLastRunAt");
             if (lastRunAt == null || lastRunAt.isBlank()) {
                 return EvaluationOutcome.fail("lastRunAt must be provided for COMPLETED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         if ("FAILED".equalsIgnoreCase(status)) {
             // FAILED should at least carry a result summary explaining failure and have retryCount present
             String resultSummary = getStringProperty(job, "getResultSummary");
             if (resultSummary == null || resultSummary.isBlank()) {
                 return EvaluationOutcome.fail("resultSummary should describe failure for FAILED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             Integer retryCount = getIntegerProperty(job, "getRetryCount");
             if (retryCount == null || retryCount < 0) {
                 return EvaluationOutcome.fail("retryCount must be non-null and non-negative for FAILED jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Non-terminal states are not considered persisted outcomes
         return EvaluationOutcome.fail("Job is not in a terminal persisted state: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private String getStringProperty(Job job, String methodName) {
        try {
            java.lang.reflect.Method m = job.getClass().getMethod(methodName);
            Object val = m.invoke(job);
            return val == null ? null : val.toString();
        } catch (NoSuchMethodException nsme) {
            logger.debug("Method {} not found on Job: {}", methodName, nsme.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("Error invoking {} on Job: {}", methodName, e.getMessage());
            return null;
        }
    }

    private Integer getIntegerProperty(Job job, String methodName) {
        try {
            java.lang.reflect.Method m = job.getClass().getMethod(methodName);
            Object val = m.invoke(job);
            if (val == null) return null;
            if (val instanceof Integer) return (Integer) val;
            if (val instanceof Number) return ((Number) val).intValue();
            try {
                return Integer.valueOf(val.toString());
            } catch (NumberFormatException nfe) {
                logger.debug("Cannot parse integer from {} result on Job: {}", methodName, nfe.getMessage());
                return null;
            }
        } catch (NoSuchMethodException nsme) {
            logger.debug("Method {} not found on Job: {}", methodName, nsme.getMessage());
            return null;
        } catch (Exception e) {
            logger.debug("Error invoking {} on Job: {}", methodName, e.getMessage());
            return null;
        }
    }
}