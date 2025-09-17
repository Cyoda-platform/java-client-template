package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HnItem;
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

/**
 * ValidationFailedCriterion - Check if validation has failed for an HN item
 * 
 * This criterion checks:
 * - Required fields are missing (id, type)
 * - Invalid type values
 * - Type-specific validation rules
 * 
 * Returns true if validation has failed, false if validation is successful.
 */
@Component
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ValidationFailed criteria for HnItem request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HnItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the entity
     * Returns success if validation passes, fail if validation fails
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HnItem> context) {
        HnItem entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HnItem is null");
            return EvaluationOutcome.success("Entity is null - validation failed");
        }

        // Check if required fields are missing
        if (entity.getId() == null) {
            logger.warn("HnItem ID is missing");
            return EvaluationOutcome.success("HN item ID is required - validation failed");
        }

        if (entity.getType() == null || !isValidType(entity.getType())) {
            logger.warn("HnItem type is invalid or missing: {}", entity.getType());
            return EvaluationOutcome.success("Invalid or missing HN item type - validation failed");
        }

        // Check type-specific validation rules
        String validationError = validateTypeSpecificRules(entity);
        if (validationError != null) {
            logger.warn("HnItem type-specific validation failed: {}", validationError);
            return EvaluationOutcome.success(validationError + " - validation failed");
        }

        // If we reach here, validation has not failed
        return EvaluationOutcome.fail("Validation has not failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    /**
     * Check if the type is valid
     */
    private boolean isValidType(String type) {
        return "job".equals(type) || "story".equals(type) || "comment".equals(type) || 
               "poll".equals(type) || "pollopt".equals(type);
    }

    /**
     * Validate type-specific business rules
     * Returns error message if validation fails, null if validation passes
     */
    private String validateTypeSpecificRules(HnItem entity) {
        String type = entity.getType();
        
        if ("comment".equals(type) && entity.getParent() == null) {
            return "Comment must have parent";
        }
        
        if ("pollopt".equals(type) && entity.getPoll() == null) {
            return "Poll option must reference poll";
        }
        
        if ("poll".equals(type) && (entity.getParts() == null || entity.getParts().isEmpty())) {
            return "Poll must have parts";
        }
        
        return null; // No validation errors
    }
}
