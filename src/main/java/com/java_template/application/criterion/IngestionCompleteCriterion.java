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
public class IngestionCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionCompleteCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion is relevant when a job finishes ingesting. Ensure job is currently INGESTING.
         String state = job.getState();
         if (state == null || !state.equals("INGESTING")) {
             return EvaluationOutcome.fail("Job is not in INGESTING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Completed timestamp must be present to mark ingestion complete
         String completedTs = job.getCompletedTimestamp();
         if (completedTs == null || completedTs.isBlank()) {
             return EvaluationOutcome.fail("completedTimestamp is required to mark ingestion complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Summary must be present and valid
         Job.Summary summary = job.getSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("summary is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getErrors() == null) {
             return EvaluationOutcome.fail("summary.errors must be present (can be empty list)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getFailedCount() == null) {
             return EvaluationOutcome.fail("summary.failedCount is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getIngestedCount() == null) {
             return EvaluationOutcome.fail("summary.ingestedCount is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If there are failures reported, consider it a data quality failure for this criterion
         if (summary.getFailedCount() > 0) {
             String msg = String.format("Ingestion completed with %d failed records", summary.getFailedCount());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> success
         return EvaluationOutcome.success();
    }
}