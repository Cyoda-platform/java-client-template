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
public class IngestionFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionFailureCriterion(SerializerFactory serializerFactory) {
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

        // status is required for decision making
        if (job.getStatus() == null || job.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String status = job.getStatus().trim();

        // If already marked as FAILED, criterion holds (ready for FAILED transition / already failed)
        if (status.equalsIgnoreCase("FAILED")) {
            return EvaluationOutcome.success();
        }

        // This criterion is only applicable while ingestion is in progress (INGESTING)
        if (!status.equalsIgnoreCase("INGESTING")) {
            return EvaluationOutcome.fail("Criterion applicable only when job.status is INGESTING or FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // At this point status == INGESTING. Determine if ingestion has produced failure indicators.
        Job.ResultSummary resultSummary = job.getResultSummary();

        // If ingestion finished but no result summary, that's a validation problem
        if (job.getFinishedAt() != null && (resultSummary == null || resultSummary.getErrorCount() == null)) {
            return EvaluationOutcome.fail("ResultSummary or errorCount missing after ingestion finished", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If result summary is missing and ingestion not finished yet, don't trigger failure
        if (resultSummary == null) {
            return EvaluationOutcome.fail("Ingestion not completed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Integer errorCount = resultSummary.getErrorCount();
        if (errorCount == null) {
            return EvaluationOutcome.fail("resultSummary.errorCount is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Primary failure condition: any reported errors
        if (errorCount > 0) {
            return EvaluationOutcome.success();
        }

        // Secondary failure condition: explicit error details present
        if (job.getErrorDetails() != null && !job.getErrorDetails().isEmpty()) {
            return EvaluationOutcome.success();
        }

        // No failure indicators found
        return EvaluationOutcome.fail("No ingestion errors detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}