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
 * IsValidHnItemCriterion - Validates if HN item is ready for indexing
 * 
 * This criterion is used in the index_item transition to ensure that
 * only valid HN items proceed to the indexing stage. It performs:
 * - Basic entity validation
 * - Required field checks
 * - Type-specific validation rules
 * - Data quality checks
 */
@Component
public class IsValidHnItemCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidHnItemCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HnItem validity criteria for request: {}", request.getId());
        
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
     * Main validation logic for the HN item
     * 
     * Validates that the HN item meets all requirements for indexing:
     * - Entity is not null
     * - Basic entity validation passes
     * - Required fields are present
     * - Type-specific validation rules pass
     * - Data quality checks pass
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HnItem> context) {
        HnItem entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HnItem is null");
            return EvaluationOutcome.fail("HnItem entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validation
        if (!entity.isValid()) {
            logger.warn("HnItem {} failed basic validation", entity.getId());
            return EvaluationOutcome.fail("HnItem failed basic validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check required fields
        EvaluationOutcome requiredFieldsCheck = validateRequiredFields(entity);
        if (!requiredFieldsCheck.isSuccess()) {
            return requiredFieldsCheck;
        }

        // Check type-specific validation rules
        EvaluationOutcome typeSpecificCheck = validateTypeSpecificRules(entity);
        if (!typeSpecificCheck.isSuccess()) {
            return typeSpecificCheck;
        }

        // Check data quality
        EvaluationOutcome dataQualityCheck = validateDataQuality(entity);
        if (!dataQualityCheck.isSuccess()) {
            return dataQualityCheck;
        }

        logger.debug("HnItem {} passed all validation criteria", entity.getId());
        return EvaluationOutcome.success();
    }

    /**
     * Validates required fields are present and valid
     */
    private EvaluationOutcome validateRequiredFields(HnItem entity) {
        // ID must be present and positive
        if (entity.getId() == null || entity.getId() <= 0) {
            logger.warn("HnItem has invalid ID: {}", entity.getId());
            return EvaluationOutcome.fail("HnItem ID must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Type must be present and valid
        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            logger.warn("HnItem {} has no type", entity.getId());
            return EvaluationOutcome.fail("HnItem type is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate type is one of allowed values
        if (!isValidType(entity.getType())) {
            logger.warn("HnItem {} has invalid type: {}", entity.getId(), entity.getType());
            return EvaluationOutcome.fail("Invalid HnItem type: " + entity.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates type-specific business rules
     */
    private EvaluationOutcome validateTypeSpecificRules(HnItem entity) {
        String type = entity.getType();
        
        switch (type) {
            case "story":
                return validateStoryRules(entity);
            case "comment":
                return validateCommentRules(entity);
            case "job":
                return validateJobRules(entity);
            case "poll":
                return validatePollRules(entity);
            case "pollopt":
                return validatePollOptRules(entity);
            default:
                return EvaluationOutcome.fail("Unknown HnItem type: " + type, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }

    /**
     * Validates story-specific rules
     */
    private EvaluationOutcome validateStoryRules(HnItem entity) {
        // Stories should have either title or URL (or both)
        if ((entity.getTitle() == null || entity.getTitle().trim().isEmpty()) && 
            (entity.getUrl() == null || entity.getUrl().trim().isEmpty())) {
            logger.warn("Story {} has neither title nor URL", entity.getId());
            return EvaluationOutcome.fail("Story must have title or URL", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }

    /**
     * Validates comment-specific rules
     */
    private EvaluationOutcome validateCommentRules(HnItem entity) {
        // Comments should have text
        if (entity.getText() == null || entity.getText().trim().isEmpty()) {
            logger.warn("Comment {} has no text", entity.getId());
            return EvaluationOutcome.fail("Comment must have text", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        // Comments should have parent (except for top-level comments)
        if (entity.getParent() == null) {
            logger.debug("Comment {} has no parent (might be top-level)", entity.getId());
            // This is not necessarily an error, so we don't fail
        }
        
        return EvaluationOutcome.success();
    }

    /**
     * Validates job-specific rules
     */
    private EvaluationOutcome validateJobRules(HnItem entity) {
        // Jobs should have title
        if (entity.getTitle() == null || entity.getTitle().trim().isEmpty()) {
            logger.warn("Job {} has no title", entity.getId());
            return EvaluationOutcome.fail("Job must have title", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        return EvaluationOutcome.success();
    }

    /**
     * Validates poll-specific rules
     */
    private EvaluationOutcome validatePollRules(HnItem entity) {
        // Polls should have title
        if (entity.getTitle() == null || entity.getTitle().trim().isEmpty()) {
            logger.warn("Poll {} has no title", entity.getId());
            return EvaluationOutcome.fail("Poll must have title", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        // Polls should have parts
        if (entity.getParts() == null || entity.getParts().isEmpty()) {
            logger.warn("Poll {} has no parts", entity.getId());
            return EvaluationOutcome.fail("Poll must have parts", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }

    /**
     * Validates poll option-specific rules
     */
    private EvaluationOutcome validatePollOptRules(HnItem entity) {
        // Poll options should have text
        if (entity.getText() == null || entity.getText().trim().isEmpty()) {
            logger.warn("Poll option {} has no text", entity.getId());
            return EvaluationOutcome.fail("Poll option must have text", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        // Poll options should reference a poll
        if (entity.getPoll() == null) {
            logger.warn("Poll option {} has no poll reference", entity.getId());
            return EvaluationOutcome.fail("Poll option must reference a poll", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }

    /**
     * Validates data quality aspects
     */
    private EvaluationOutcome validateDataQuality(HnItem entity) {
        // Check for reasonable timestamp
        if (entity.getTime() != null && entity.getTime() < 0) {
            logger.warn("HnItem {} has negative timestamp: {}", entity.getId(), entity.getTime());
            return EvaluationOutcome.fail("Invalid timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        
        // Check for reasonable score
        if (entity.getScore() != null && entity.getScore() < 0) {
            logger.warn("HnItem {} has negative score: {}", entity.getId(), entity.getScore());
            return EvaluationOutcome.fail("Invalid score", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        
        // Check for reasonable descendants count
        if (entity.getDescendants() != null && entity.getDescendants() < 0) {
            logger.warn("HnItem {} has negative descendants count: {}", entity.getId(), entity.getDescendants());
            return EvaluationOutcome.fail("Invalid descendants count", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        
        return EvaluationOutcome.success();
    }

    /**
     * Validates if the type is one of the allowed HN item types
     */
    private boolean isValidType(String type) {
        return "story".equals(type) || 
               "comment".equals(type) || 
               "job".equals(type) || 
               "poll".equals(type) || 
               "pollopt".equals(type);
    }
}
