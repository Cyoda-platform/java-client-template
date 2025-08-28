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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("IngestionCompleteCriterion invoked with null job");
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion is relevant when a job finishes ingesting. Ensure job is currently INGESTING.
         String state = job.getState();
         if (state == null || !state.equals("INGESTING")) {
             logger.debug("Job [{}] not in INGESTING state (state={}) - skipping IngestionCompleteCriterion", job.getId(), state);
             return EvaluationOutcome.fail("Job is not in INGESTING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Completed timestamp must be present to mark ingestion complete
         String completedTs = job.getCompletedTimestamp();
         if (completedTs == null || completedTs.isBlank()) {
             logger.warn("Job [{}] marked INGESTING but completedTimestamp is missing", job.getId());
             return EvaluationOutcome.fail("completedTimestamp is required to mark ingestion complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Summary must be present and valid
         Job.Summary summary = job.getSummary();
         if (summary == null) {
             logger.warn("Job [{}] completedTimestamp present but summary is missing", job.getId());
             return EvaluationOutcome.fail("summary is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getErrors() == null) {
             logger.warn("Job [{}] summary.errors is null", job.getId());
             return EvaluationOutcome.fail("summary.errors must be present (can be empty list)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getFailedCount() == null) {
             logger.warn("Job [{}] summary.failedCount is null", job.getId());
             return EvaluationOutcome.fail("summary.failedCount is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getIngestedCount() == null) {
             logger.warn("Job [{}] summary.ingestedCount is null", job.getId());
             return EvaluationOutcome.fail("summary.ingestedCount is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If there are failures reported, consider it a data quality failure for this criterion
         if (summary.getFailedCount() > 0) {
             String msg = String.format("Ingestion completed with %d failed records", summary.getFailedCount());
             logger.info("Job [{}] ingestion completed with failures: {}", job.getId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If ingested count is zero, treat as data quality failure as well (unlikely successful ingestion)
         if (summary.getIngestedCount() == 0) {
             String msg = "Ingestion completed but ingestedCount is 0";
             logger.info("Job [{}] ingestion completed but no records ingested", job.getId());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> success
         logger.info("Job [{}] ingestion completed successfully (ingested={}, failed={})", job.getId(), summary.getIngestedCount(), summary.getFailedCount());
         return EvaluationOutcome.success();
    }
}