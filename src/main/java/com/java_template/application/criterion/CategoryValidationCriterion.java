package com.java_template.application.criterion;

import com.java_template.application.entity.category.version_1.Category;
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

import java.util.regex.Pattern;

/**
 * CategoryValidationCriterion - Validate category before activation
 * 
 * Transition: activate_category (none → active)
 * Purpose: Validate category before activation
 */
@Component
public class CategoryValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    // Pattern for allowed characters in category name (letters, numbers, spaces, hyphens)
    private static final Pattern CATEGORY_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9\\s\\-]+$");

    public CategoryValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Category validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Category.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Category> context) {
        Category category = context.entityWithMetadata().entity();

        // Check if category is null (structural validation)
        if (category == null) {
            logger.warn("Category is null");
            return EvaluationOutcome.fail("Category is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!category.isValid()) {
            logger.warn("Category is not valid");
            return EvaluationOutcome.fail("Category is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Category name must not be null or empty (already checked in isValid())
        String categoryName = category.getName();
        if (categoryName == null || categoryName.trim().isEmpty()) {
            logger.warn("Category name is null or empty");
            return EvaluationOutcome.fail("Category name must not be null or empty", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // 1. Check if category name has reasonable length (< 100 characters)
        if (categoryName.length() >= 100) {
            logger.warn("Category name is too long: {} characters", categoryName.length());
            return EvaluationOutcome.fail("Category name must be less than 100 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 2. Validate category name contains only allowed characters (letters, numbers, spaces, hyphens)
        if (!CATEGORY_NAME_PATTERN.matcher(categoryName).matches()) {
            logger.warn("Category name contains invalid characters: {}", categoryName);
            return EvaluationOutcome.fail("Category name can only contain letters, numbers, spaces, and hyphens", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // 3. Verify category name is unique (simplified - cannot check without external service)
        // In a real system, this would require checking existing categories via EntityService, which is not recommended in criteria
        // This validation would be better handled at the database level or in a processor

        // 4. Check if description length is reasonable (< 500 characters)
        if (category.getDescription() != null && category.getDescription().length() >= 500) {
            logger.warn("Category description is too long: {} characters", category.getDescription().length());
            return EvaluationOutcome.fail("Category description must be less than 500 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Category validation passed for category: {}", category.getCategoryId());
        return EvaluationOutcome.success();
    }
}
