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
public class TransformFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public TransformFailureCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();
         if (entity == null) {
             logger.warn("TransformFailureCriterion: entity is null in context");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             logger.warn("TransformFailureCriterion: ingestion job status is missing for id={}", entity.getId());
             return EvaluationOutcome.fail("Ingestion job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Criterion purpose: detect that a transform has failed. If the job reports status "FAILED",
         // the criterion is satisfied (success) so the workflow can transition to the FAILED state.
         if ("FAILED".equalsIgnoreCase(status.trim())) {
             logger.info("TransformFailureCriterion: detected FAILED status for ingestion job id={}", entity.getId());
             return EvaluationOutcome.success();
         }

         // Not in failed state -> criterion not satisfied
         logger.debug("TransformFailureCriterion: ingestion job not in FAILED state (status={}) for id={}", status, entity.getId());
         return EvaluationOutcome.fail("No transform failure detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}