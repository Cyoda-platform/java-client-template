package com.java_template.application.criterion;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
public class TransformCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public TransformCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name match (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob job = context.entity();

         // Ensure metadata exists
         Map<String, Object> metadata = job.getMetadata();
         if (metadata == null) {
             return EvaluationOutcome.fail("Missing job metadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Extract numeric counts from metadata
         Integer fetchedCount = toInteger(metadata.get("fetched_count"));
         if (fetchedCount == null) {
             return EvaluationOutcome.fail("Missing fetched_count in job metadata", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer transformedCount = toInteger(metadata.get("transformed_count"));
         if (transformedCount == null) {
             return EvaluationOutcome.fail("Missing transformed_count in job metadata", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer failedCount = toInteger(metadata.get("failed_count"));
         if (failedCount == null) {
             // treat absent failed_count as zero (common pattern)
             failedCount = 0;
         }

         // Basic data quality checks
         if (fetchedCount < 0 || transformedCount < 0 || failedCount < 0) {
             return EvaluationOutcome.fail("Negative counts present in metadata", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: all fetched records must be accounted for (transformed + failed == fetched)
         if (transformedCount + failedCount != fetchedCount) {
             String msg = String.format("Mismatch in counts: fetched_count=%d, transformed_count=%d, failed_count=%d",
                     fetchedCount, transformedCount, failedCount);
             logger.warn("{} - {}", className, msg);
             return EvaluationOutcome.fail("transformed_count + failed_count does not equal fetched_count", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Optional: ensure there is at least one transformed record when fetched_count > 0
         if (fetchedCount > 0 && transformedCount == 0) {
             return EvaluationOutcome.fail("No records were transformed while fetched_count > 0", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                String s = ((String) value).trim();
                if (s.isEmpty()) return null;
                return Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                logger.debug("Failed to parse integer from string metadata value '{}'", value);
                return null;
            }
        }
        return null;
    }
}