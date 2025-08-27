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
         Job entity = context.entity();

         // Basic presence checks
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion is intended to be evaluated while the job is in INGESTING state.
         if (!"INGESTING".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Job is not in INGESTING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // resultSummary must be present and valid to decide success
         Job.ResultSummary summary = entity.getResultSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("Missing resultSummary for ingestion outcome", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!summary.isValid()) {
             return EvaluationOutcome.fail("Invalid resultSummary counts", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If any errors were recorded, consider ingestion not successful
         if (summary.getErrorCount() != null && summary.getErrorCount() > 0) {
             String msg = String.format("Ingestion completed with %d errors", summary.getErrorCount());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // No errors -> success
         return EvaluationOutcome.success();
    }
}