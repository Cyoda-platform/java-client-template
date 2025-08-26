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

import java.lang.reflect.Method;

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
         Object entity = context.entity();

         // Use reflection to access getters to avoid compile-time coupling to a specific entity API.
         String laureateId = getString(entity, "getLaureateId");
         String fullName = getString(entity, "getFullName");
         Integer prizeYear = getInteger(entity, "getPrizeYear");
         String detectedAt = getString(entity, "getDetectedAt");
         String changeType = getString(entity, "getChangeType");
         Boolean published = getBoolean(entity, "getPublished");
         String rawPayload = getString(entity, "getRawPayload");

         // Basic required-field validation (use only getters present on the entity)
         if (laureateId == null || laureateId.isBlank()) {
             logger.debug("Deduplication failed: missing laureateId");
             return EvaluationOutcome.fail("laureateId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (fullName == null || fullName.isBlank()) {
             logger.debug("Deduplication failed: missing fullName for laureateId={}", laureateId);
             return EvaluationOutcome.fail("fullName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (prizeYear == null) {
             logger.debug("Deduplication failed: missing prizeYear for laureateId={}", laureateId);
             return EvaluationOutcome.fail("prizeYear is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (detectedAt == null || detectedAt.isBlank()) {
             logger.debug("Deduplication failed: missing detectedAt for laureateId={}", laureateId);
             return EvaluationOutcome.fail("detectedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // changeType must be present and one of expected values
         if (changeType == null || changeType.isBlank()) {
             logger.debug("Deduplication failed: missing changeType for laureateId={}", laureateId);
             return EvaluationOutcome.fail("changeType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String normalized = changeType.trim().toLowerCase();
         if (!normalized.equals("new") && !normalized.equals("updated") && !normalized.equals("deleted")) {
             logger.debug("Deduplication failed: invalid changeType='{}' for laureateId={}", changeType, laureateId);
             return EvaluationOutcome.fail("changeType must be one of [new, updated, deleted]", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic deduplication/business-rule checks using available fields only:
         // - If an incoming event claims to be "new" but the entity is already marked published -> likely duplicate
         if (normalized.equals("new") && Boolean.TRUE.equals(published)) {
             logger.info("Deduplication detected likely duplicate: laureateId={} marked as published but incoming changeType='new'", laureateId);
             return EvaluationOutcome.fail("Laureate already published but incoming changeType is 'new' - possible duplicate", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // - If event is "updated" but entity is not published yet, this is an inconsistent update (business rule)
         if (normalized.equals("updated") && !Boolean.TRUE.equals(published)) {
             logger.info("Deduplication/business rule: update received for unpublished laureateId={}", laureateId);
             return EvaluationOutcome.fail("Update received for an unpublished laureate - inconsistent state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // - If event is "deleted" but entity not published and no rawPayload -> suspicious (data quality)
         if (normalized.equals("deleted") && !Boolean.TRUE.equals(published) && (rawPayload == null || rawPayload.isBlank())) {
             logger.info("Deduplication/data quality: delete received for non-published laureateId={} without raw payload", laureateId);
             return EvaluationOutcome.fail("Delete received for a non-published laureate with no raw payload - suspicious", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If none of the deduplication heuristics triggered, accept entity for downstream processing
         logger.debug("Deduplication passed for laureateId={}", laureateId);
         return EvaluationOutcome.success();
    }

    private Object invokeGetter(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            logger.debug("Missing method {} on {}", methodName, target.getClass().getName());
        } catch (Exception e) {
            logger.debug("Failed to invoke {} on {}: {}", methodName, target.getClass().getName(), e.toString());
        }
        return null;
    }

    private String getString(Object target, String methodName) {
        Object val = invokeGetter(target, methodName);
        return val == null ? null : String.valueOf(val);
    }

    private Integer getInteger(Object target, String methodName) {
        Object val = invokeGetter(target, methodName);
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (NumberFormatException e) {
                logger.debug("Failed to parse integer from {} returned by {}", val, methodName);
            }
        }
        return null;
    }

    private Boolean getBoolean(Object target, String methodName) {
        Object val = invokeGetter(target, methodName);
        if (val == null) return null;
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean(((String) val).trim());
        return null;
    }
}