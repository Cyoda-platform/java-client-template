package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class DeduplicationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Basic required-field validation (use only getters present on the entity)
         if (entity.getLaureateId() == null || entity.getLaureateId().isBlank()) {
             logger.debug("Deduplication failed: missing laureateId");
             return EvaluationOutcome.fail("laureateId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFullName() == null || entity.getFullName().isBlank()) {
             logger.debug("Deduplication failed: missing fullName for laureateId={}", entity.getLaureateId());
             return EvaluationOutcome.fail("fullName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrizeYear() == null) {
             logger.debug("Deduplication failed: missing prizeYear for laureateId={}", entity.getLaureateId());
             return EvaluationOutcome.fail("prizeYear is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDetectedAt() == null || entity.getDetectedAt().isBlank()) {
             logger.debug("Deduplication failed: missing detectedAt for laureateId={}", entity.getLaureateId());
             return EvaluationOutcome.fail("detectedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // changeType must be present and one of expected values
         String changeType = entity.getChangeType();
         if (changeType == null || changeType.isBlank()) {
             logger.debug("Deduplication failed: missing changeType for laureateId={}", entity.getLaureateId());
             return EvaluationOutcome.fail("changeType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String normalized = changeType.trim().toLowerCase();
         if (!normalized.equals("new") && !normalized.equals("updated") && !normalized.equals("deleted")) {
             logger.debug("Deduplication failed: invalid changeType='{}' for laureateId={}", changeType, entity.getLaureateId());
             return EvaluationOutcome.fail("changeType must be one of [new, updated, deleted]", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic deduplication/business-rule checks using available fields only:
         // - If an incoming event claims to be "new" but the entity is already marked published -> likely duplicate
         if (normalized.equals("new") && Boolean.TRUE.equals(entity.getPublished())) {
             logger.info("Deduplication detected likely duplicate: laureateId={} marked as published but incoming changeType='new'", entity.getLaureateId());
             return EvaluationOutcome.fail("Laureate already published but incoming changeType is 'new' - possible duplicate", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // - If event is "updated" but entity is not published yet, this is an inconsistent update (business rule)
         if (normalized.equals("updated") && !Boolean.TRUE.equals(entity.getPublished())) {
             logger.info("Deduplication/business rule: update received for unpublished laureateId={}", entity.getLaureateId());
             return EvaluationOutcome.fail("Update received for an unpublished laureate - inconsistent state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // - If event is "deleted" but entity not published and no rawPayload -> suspicious (data quality)
         if (normalized.equals("deleted") && !Boolean.TRUE.equals(entity.getPublished()) && (entity.getRawPayload() == null || entity.getRawPayload().isBlank())) {
             logger.info("Deduplication/data quality: delete received for non-published laureateId={} without raw payload", entity.getLaureateId());
             return EvaluationOutcome.fail("Delete received for a non-published laureate with no raw payload - suspicious", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If none of the deduplication heuristics triggered, accept entity for downstream processing
         logger.debug("Deduplication passed for laureateId={}", entity.getLaureateId());
         return EvaluationOutcome.success();
    }
}