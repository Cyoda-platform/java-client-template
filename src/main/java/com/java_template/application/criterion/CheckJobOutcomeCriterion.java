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
public class CheckJobOutcomeCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckJobOutcomeCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields for outcome decision
         if (job.getState() == null || job.getState().isBlank()) {
             return EvaluationOutcome.fail("Job state is required to determine outcome", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getStartedAt() == null || job.getStartedAt().isBlank()) {
             return EvaluationOutcome.fail("Job startedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // We require finishedAt to determine final outcome
         if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
             return EvaluationOutcome.fail("Job has not finished; finishedAt is required to determine outcome", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer fetched = job.getRecordsFetchedCount();
         Integer processed = job.getRecordsProcessedCount();
         Integer failed = job.getRecordsFailedCount();

         // Data quality checks for counts
         if (fetched != null && fetched < 0) {
             return EvaluationOutcome.fail("recordsFetchedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processed != null && processed < 0) {
             return EvaluationOutcome.fail("recordsProcessedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (failed != null && failed < 0) {
             return EvaluationOutcome.fail("recordsFailedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If we have all counts present, ensure they are consistent
         if (fetched != null && processed != null && failed != null) {
             if (processed + failed != fetched) {
                 return EvaluationOutcome.fail("Inconsistent record counts: fetched != processed + failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Determine fatal error conditions:
         // - explicit error summary present together with failed records
         // - all fetched records failed
         // - no records fetched (interpreted as failure of ingestion)
         boolean hasErrorSummary = job.getErrorSummary() != null && !job.getErrorSummary().isBlank();
         if (fetched != null && fetched == 0) {
             return EvaluationOutcome.fail("No records fetched during ingestion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (failed != null && fetched != null && failed.equals(fetched) && fetched > 0) {
             return EvaluationOutcome.fail("All fetched records failed ingestion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (hasErrorSummary && (failed == null || failed > 0)) {
             return EvaluationOutcome.fail("Errors reported during ingestion: " + job.getErrorSummary(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If none of the fatal conditions matched, we consider the job succeeded
         return EvaluationOutcome.success();
    }
}