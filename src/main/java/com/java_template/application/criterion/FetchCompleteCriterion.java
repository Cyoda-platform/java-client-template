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
public class FetchCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must match the criterion name exactly
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();
         if (entity == null) {
             logger.debug("FetchCompleteCriterion: received null entity");
             return EvaluationOutcome.fail("entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String jobName = String.valueOf(entity.getJobName());

         // lastRunTimestamp must be present - fetch run should set this
         if (entity.getLastRunTimestamp() == null || entity.getLastRunTimestamp().isBlank()) {
             logger.debug("FetchCompleteCriterion: missing lastRunTimestamp for job {}", jobName);
             return EvaluationOutcome.fail("last_run_timestamp is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // metadata map must contain fetched_count
         Map<String, Object> metadata = entity.getMetadata();
         if (metadata == null) {
             logger.debug("FetchCompleteCriterion: metadata is null for job {}", jobName);
             return EvaluationOutcome.fail("metadata is required and must contain fetched_count", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Object fetchedCountObj = metadata.get("fetched_count");
         if (fetchedCountObj == null) {
             logger.debug("FetchCompleteCriterion: fetched_count missing in metadata for job {}", jobName);
             return EvaluationOutcome.fail("metadata.fetched_count is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         int fetchedCount;
         if (fetchedCountObj instanceof Number) {
             fetchedCount = ((Number) fetchedCountObj).intValue();
         } else if (fetchedCountObj instanceof String) {
             try {
                 fetchedCount = Integer.parseInt(((String) fetchedCountObj).trim());
             } catch (NumberFormatException e) {
                 logger.debug("FetchCompleteCriterion: fetched_count not numeric for job {}: {}", jobName, fetchedCountObj);
                 return EvaluationOutcome.fail("metadata.fetched_count is not a valid number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             logger.debug("FetchCompleteCriterion: fetched_count has unsupported type for job {}: {}", jobName, fetchedCountObj.getClass());
             return EvaluationOutcome.fail("metadata.fetched_count has unsupported type", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: at least one user should have been fetched to consider fetch complete
         if (fetchedCount <= 0) {
             logger.debug("FetchCompleteCriterion: fetched_count is zero or negative for job {}: {}", jobName, fetchedCount);
             return EvaluationOutcome.fail("no records were fetched (fetched_count <= 0)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed -> fetch is considered complete
         logger.debug("FetchCompleteCriterion: fetch complete for job {} with fetched_count={}", jobName, fetchedCount);
         return EvaluationOutcome.success();
    }
}