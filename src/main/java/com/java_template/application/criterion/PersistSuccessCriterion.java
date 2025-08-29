package com.java_template.application.criterion;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("IngestionJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields validation
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toUpperCase();

         // Business rule: persistence is considered successful only when status == COMPLETED
         if (!"COMPLETED".equals(status)) {
             // Distinguish explicit failure vs other states
             if ("FAILED".equals(status)) {
                 return EvaluationOutcome.fail("Ingestion job persistence failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             } else {
                 return EvaluationOutcome.fail("Ingestion job not completed (status=" + entity.getStatus() + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Data quality: when completed, lastRunAt must be present
         if (entity.getLastRunAt() == null || entity.getLastRunAt().isBlank()) {
             return EvaluationOutcome.fail("lastRunAt missing after completion", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}