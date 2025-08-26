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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact criterion name match (case-sensitive) as required.
        return modelSpec != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If there are explicit error details recorded, treat as unrecoverable business failure.
         String errorDetails = job.getErrorDetails();
         if (errorDetails != null && !errorDetails.isBlank()) {
             return EvaluationOutcome.fail("Job reports error: " + errorDetails, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         Job.IngestionSummary summary = job.getIngestionSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("ingestionSummary is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Integer processed = summary.getRecordsProcessed();
         if (processed == null || processed <= 0) {
             return EvaluationOutcome.fail("No records processed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Success: there were processed records and no unrecoverable error details.
         return EvaluationOutcome.success();
    }
}