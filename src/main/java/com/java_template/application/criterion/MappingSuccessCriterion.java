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
public class MappingSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MappingSuccessCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         // Basic validation: required status
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Mapping success expects the job to be COMPLETED
         if (!"COMPLETED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Ingestion job status is not COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // completedAt must be present for completed jobs
         if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
             return EvaluationOutcome.fail("completedAt must be set when status is COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // summary must be present and well-formed
         IngestionJob.Summary summary = entity.getSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("summary is required to evaluate mapping results", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getCreated() == null || summary.getUpdated() == null || summary.getFailed() == null) {
             return EvaluationOutcome.fail("summary counts (created/updated/failed) must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (summary.getCreated() < 0 || summary.getUpdated() < 0 || summary.getFailed() < 0) {
             return EvaluationOutcome.fail("summary counts must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business expectation: no failed mappings for a successful mapping pass
         if (summary.getFailed() > 0) {
             return EvaluationOutcome.fail("There are failed mappings reported in summary", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // At least one record should have been created or updated for a meaningful mapping
         if (summary.getCreated() + summary.getUpdated() <= 0) {
             return EvaluationOutcome.fail("No records were created or updated during mapping", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}