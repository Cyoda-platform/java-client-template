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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return "FetchSuccessCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();
         if (entity == null) {
             logger.warn("IngestionJob entity is null in FetchSuccessCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate required configuration for fetch
         if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required for fetch", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDataFormats() == null || entity.getDataFormats().isBlank()) {
             return EvaluationOutcome.fail("dataFormats must be specified (e.g., JSON,XML)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present to evaluate fetch outcome
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is missing for ingestion job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = entity.getStatus().trim();
         // If the job reports completed, ensure lastRunAt is present
         if ("COMPLETED".equalsIgnoreCase(status)) {
             if (entity.getLastRunAt() == null || entity.getLastRunAt().isBlank()) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but lastRunAt is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Explicit failure reported by job
         if ("FAILED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Fetch operation failed for ingestion job", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Other states (PENDING, RUNNING, etc.) mean fetch not yet successful
         return EvaluationOutcome.fail("Fetch not completed yet (status: " + status + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}