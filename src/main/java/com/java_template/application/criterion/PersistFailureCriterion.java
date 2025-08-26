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

import java.lang.reflect.Method;

@Component
public class PersistFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistFailureCriterion(SerializerFactory serializerFactory) {
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
        // must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
        Job job = context.entity();

        // Use reflection to access getters since Job's accessor names may vary across versions.
        String status = getStringGetter(job, "Status");

        // Basic validation: status must be present
        if (status == null || status.isBlank()) {
            return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        status = status.trim();

        // If job completed, ensure resultSummary is present to confirm persistence produced a summary
        if ("COMPLETED".equalsIgnoreCase(status)) {
            String resultSummary = getStringGetter(job, "ResultSummary");
            if (resultSummary == null || resultSummary.isBlank()) {
                return EvaluationOutcome.fail("Completed job missing resultSummary", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            return EvaluationOutcome.success();
        }

        // If job failed, evaluate retry strategy and surface meaningful failure reasons
        if ("FAILED".equalsIgnoreCase(status)) {
            Integer retries = getIntegerGetter(job, "RetryCount");

            // retryCount must be provided for failed jobs
            if (retries == null) {
                return EvaluationOutcome.fail("retryCount must be set for failed jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

            // Business rule: if retries have been exhausted (3 or more), treat as business rule failure
            if (retries >= 3) {
                return EvaluationOutcome.fail("Job has failed after maximum retry attempts", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }

            // Otherwise it's a transient/data issue eligible for retry
            return EvaluationOutcome.fail(
                String.format("Job has failed and is eligible for retry (attempts=%d)", retries),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }

        // For any other status values, no persistence failure detected by this criterion
        return EvaluationOutcome.success();
    }

    private String getStringGetter(Object obj, String propSuffix) {
        if (obj == null) {
            return null;
        }
        try {
            Method m = obj.getClass().getMethod("get" + propSuffix);
            Object val = m.invoke(obj);
            return val == null ? null : val.toString();
        } catch (ReflectiveOperationException e) {
            logger.debug("Unable to invoke getter get{} on {}: {}", propSuffix, obj.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private Integer getIntegerGetter(Object obj, String propSuffix) {
        if (obj == null) {
            return null;
        }
        try {
            Method m = obj.getClass().getMethod("get" + propSuffix);
            Object val = m.invoke(obj);
            if (val == null) {
                return null;
            }
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            try {
                return Integer.valueOf(val.toString());
            } catch (NumberFormatException nfe) {
                logger.debug("Unable to parse integer from property get{} value '{}' on {}: {}", propSuffix, val, obj.getClass().getName(), nfe.getMessage());
                return null;
            }
        } catch (ReflectiveOperationException e) {
            logger.debug("Unable to invoke getter get{} on {}: {}", propSuffix, obj.getClass().getName(), e.getMessage());
            return null;
        }
    }
}