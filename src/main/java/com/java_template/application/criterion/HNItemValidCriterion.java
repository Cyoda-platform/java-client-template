package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
 * HNItemValidCriterion - Validates HNItem data integrity and business rules
 * 
 * This criterion validates HNItem entities according to the business rules:
 * 1. id must not be null
 * 2. type must be one of: "job", "story", "comment", "poll", "pollopt"
 * 3. If type is "comment", parent should not be null
 * 4. If type is "poll", parts should not be empty
 * 5. If type is "pollopt", poll should not be null
 */
@Component
public class HNItemValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HNItemValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking HNItem criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the HNItem entity
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("HNItem is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate required field: id
        if (entity.getId() == null) {
            logger.warn("HNItem id is null");
            return EvaluationOutcome.fail("HNItem id must not be null", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate required field: type
        if (entity.getType() == null) {
            logger.warn("HNItem type is null for item: {}", entity.getId());
            return EvaluationOutcome.fail("HNItem type must not be null", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate type is one of allowed values
        if (!isValidType(entity.getType())) {
            logger.warn("HNItem type '{}' is invalid for item: {}", entity.getType(), entity.getId());
            return EvaluationOutcome.fail(
                String.format("HNItem type '%s' must be one of: job, story, comment, poll, pollopt", entity.getType()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Business rule: If type is "comment", parent should not be null
        if ("comment".equals(entity.getType()) && entity.getParent() == null) {
            logger.warn("Comment HNItem {} missing parent", entity.getId());
            return EvaluationOutcome.fail(
                "Comment items must have a parent",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Business rule: If type is "poll", parts should not be empty
        if ("poll".equals(entity.getType()) && (entity.getParts() == null || entity.getParts().isEmpty())) {
            logger.warn("Poll HNItem {} missing parts", entity.getId());
            return EvaluationOutcome.fail(
                "Poll items must have parts",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Business rule: If type is "pollopt", poll should not be null
        if ("pollopt".equals(entity.getType()) && entity.getPoll() == null) {
            logger.warn("Poll option HNItem {} missing poll reference", entity.getId());
            return EvaluationOutcome.fail(
                "Poll option items must reference a poll",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Use entity's built-in validation
        if (!entity.isValid()) {
            logger.warn("HNItem {} failed built-in validation", entity.getId());
            return EvaluationOutcome.fail("Entity failed built-in validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.debug("HNItem {} passed all validation checks", entity.getId());
        return EvaluationOutcome.success();
    }

    /**
     * Validates if the type is one of the allowed HN item types
     */
    private boolean isValidType(String type) {
        return "job".equals(type) || 
               "story".equals(type) || 
               "comment".equals(type) || 
               "poll".equals(type) || 
               "pollopt".equals(type);
    }
}
