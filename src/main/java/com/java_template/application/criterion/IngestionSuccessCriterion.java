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
public class IngestionSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Policy constants: allow a small percentage of failed records or a reasonable absolute number
    private static final double PERCENT_THRESHOLD = 0.05d; // 5% of totalRecords
    private static final int ABSOLUTE_THRESHOLD = 5; // at least allow 5 failures

    public IngestionSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("IngestionSuccessCriterion: job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer totalRecords = job.getTotalRecords();
         Integer failedCount = job.getFailedCount();
         Integer succeededCount = job.getSucceededCount();

         // Basic presence checks for counts
         if (totalRecords == null || failedCount == null || succeededCount == null) {
             logger.warn("IngestionSuccessCriterion: missing count fields (total={}, succeeded={}, failed={})",
                     totalRecords, succeededCount, failedCount);
             return EvaluationOutcome.fail("Job counts are incomplete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (totalRecords < 0 || failedCount < 0 || succeededCount < 0) {
             logger.warn("IngestionSuccessCriterion: negative counts detected (total={}, succeeded={}, failed={})",
                     totalRecords, succeededCount, failedCount);
             return EvaluationOutcome.fail("Job contains invalid (negative) counts", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Consistency: succeeded + failed should not exceed totalRecords (Job.isValid enforces this, but double-check)
         long sum = (long) succeededCount + (long) failedCount;
         if (sum > totalRecords) {
             logger.warn("IngestionSuccessCriterion: inconsistent counts (total={}, succeeded={}, failed={})",
                     totalRecords, succeededCount, failedCount);
             return EvaluationOutcome.fail("Inconsistent job counts: succeeded + failed > totalRecords", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Determine allowed failures based on percentage and absolute threshold
         int allowedByPercent = (int) Math.ceil(totalRecords * PERCENT_THRESHOLD);
         int allowedFailures = Math.max(ABSOLUTE_THRESHOLD, allowedByPercent);

         // Special-case: if totalRecords is zero, require failedCount to be zero to consider success
         if (totalRecords == 0) {
             if (failedCount == 0) {
                 logger.info("IngestionSuccessCriterion: job {} succeeded with zero records", job.getJobId());
                 return EvaluationOutcome.success();
             } else {
                 logger.warn("IngestionSuccessCriterion: job {} has {} failed records but totalRecords is 0", job.getJobId(), failedCount);
                 return EvaluationOutcome.fail(
                         String.format("Job has failed records (%d) but totalRecords is 0", failedCount),
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Successful when no failures
         if (failedCount == 0) {
             logger.info("IngestionSuccessCriterion: job {} succeeded with zero failed records (total={})", job.getJobId(), totalRecords);
             return EvaluationOutcome.success();
         }

         // Success when failures are within computed allowance
         if (failedCount <= allowedFailures) {
             logger.info("IngestionSuccessCriterion: job {} considered successful with {} failed records (allowed={}) out of {} total",
                     job.getJobId(), failedCount, allowedFailures, totalRecords);
             return EvaluationOutcome.success();
         }

         // Otherwise consider it a data quality failure
         logger.warn("IngestionSuccessCriterion: job {} failed ingestion threshold: {} failed of {} total (allowed={})",
                 job.getJobId(), failedCount, totalRecords, allowedFailures);
         return EvaluationOutcome.fail(
                 String.format("Ingestion failed: %d failed records out of %d (allowed %d)", failedCount, totalRecords, allowedFailures),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}