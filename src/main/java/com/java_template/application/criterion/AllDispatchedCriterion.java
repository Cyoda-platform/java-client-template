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

import java.util.Map;

@Component
public class AllDispatchedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AllDispatchedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
        Job job = context.entity();

        if (job == null) {
            logger.debug("Job entity is null");
            return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        String status = job.getStatus();
        if (status == null || status.isBlank()) {
            logger.debug("Job status is missing for job id={}", job.getId());
            return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If job already completed, criterion satisfied
        if ("COMPLETED".equals(status)) {
            return EvaluationOutcome.success();
        }

        // This criterion verifies that all expected dispatches have been emitted.
        // It expects the job.parameters map to contain numeric counters:
        //  - "expectedDispatchCount" : Number
        //  - "dispatchedCount" : Number
        Map<String, Object> params = job.getParameters();

        if (params == null) {
            logger.debug("Parameters map is null for job id={}", job.getId());
            return EvaluationOutcome.fail("Missing job parameters required to evaluate dispatch progress",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        Object expectedObj = params.get("expectedDispatchCount");
        Object dispatchedObj = params.get("dispatchedCount");

        if (expectedObj == null || dispatchedObj == null) {
            logger.debug("Missing dispatch counters for job id={}, expected={}, dispatched={}", job.getId(), expectedObj, dispatchedObj);
            return EvaluationOutcome.fail("Dispatch counters not present in job parameters",
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        Long expected;
        Long dispatched;
        try {
            expected = toLong(expectedObj);
            dispatched = toLong(dispatchedObj);
        } catch (NumberFormatException ex) {
            logger.debug("Invalid dispatch counter types for job id={}: expectedObj={}, dispatchedObj={}, error={}", job.getId(), expectedObj, dispatchedObj, ex.getMessage());
            return EvaluationOutcome.fail("Dispatch counters must be numeric", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (expected == null || expected <= 0) {
            logger.debug("Non-positive expected dispatch count for job id={}: {}", job.getId(), expected);
            return EvaluationOutcome.fail("Expected dispatch count must be greater than zero",
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (dispatched == null) {
            logger.debug("Dispatched count could not be determined for job id={}", job.getId());
            return EvaluationOutcome.fail("Dispatched count is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (dispatched >= expected) {
            // All dispatched (or over-dispatched) => success
            return EvaluationOutcome.success();
        } else {
            logger.debug("Not all dispatched for job id={}: dispatched={}, expected={}", job.getId(), dispatched, expected);
            return EvaluationOutcome.fail(
                String.format("Not all records dispatched: dispatched=%d expected=%d", dispatched, expected),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
    }

    /**
     * Convert common numeric types to Long.
     * Accepts any Number, or numeric String (integer or decimal).
     * For floating values the longValue() (floor toward zero) is used.
     */
    private Long toLong(Object obj) {
        if (obj == null) throw new NumberFormatException("null");
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        if (obj instanceof String) {
            String s = ((String) obj).trim();
            if (s.isEmpty()) throw new NumberFormatException("Empty string");
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                // Try parsing as double and convert
                double d = Double.parseDouble(s);
                return (long) d;
            }
        }
        throw new NumberFormatException("Unsupported numeric type: " + obj.getClass().getName());
    }
}