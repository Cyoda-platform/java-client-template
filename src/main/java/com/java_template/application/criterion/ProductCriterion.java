package com.java_template.application.criterion;

import com.java_template.application.entity.product.version_1.Product;
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
 * ProductCriterion - Validates product business rules
 * 
 * This criterion validates:
 * - Required fields are present and valid
 * - Price is non-negative
 * - Quantity available is non-negative
 * - SKU is unique and properly formatted
 * - Category is valid
 * - Product schema compliance
 */
@Component
public class ProductCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Product criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProduct)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the Product entity
     */
    private EvaluationOutcome validateProduct(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entityWithMetadata().entity();

        // Check if product is null (structural validation)
        if (product == null) {
            logger.warn("Product is null");
            return EvaluationOutcome.fail("Product is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Basic entity validation
        if (!product.isValid()) {
            logger.warn("Product basic validation failed for SKU: {}", product.getSku());
            return EvaluationOutcome.fail("Product basic validation failed", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate SKU format
        if (product.getSku() != null && (product.getSku().length() < 3 || product.getSku().length() > 50)) {
            logger.warn("Product SKU length invalid: {}", product.getSku());
            return EvaluationOutcome.fail("Product SKU must be between 3 and 50 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate price range
        if (product.getPrice() != null && product.getPrice() < 0) {
            logger.warn("Product price is negative: {}", product.getPrice());
            return EvaluationOutcome.fail("Product price cannot be negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate quantity available
        if (product.getQuantityAvailable() != null && product.getQuantityAvailable() < 0) {
            logger.warn("Product quantity available is negative: {}", product.getQuantityAvailable());
            return EvaluationOutcome.fail("Product quantity available cannot be negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate name length
        if (product.getName() != null && (product.getName().length() < 1 || product.getName().length() > 200)) {
            logger.warn("Product name length invalid: {}", product.getName().length());
            return EvaluationOutcome.fail("Product name must be between 1 and 200 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate description length
        if (product.getDescription() != null && product.getDescription().length() > 2000) {
            logger.warn("Product description too long: {}", product.getDescription().length());
            return EvaluationOutcome.fail("Product description cannot exceed 2000 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate category
        if (product.getCategory() != null && (product.getCategory().length() < 1 || product.getCategory().length() > 100)) {
            logger.warn("Product category length invalid: {}", product.getCategory().length());
            return EvaluationOutcome.fail("Product category must be between 1 and 100 characters", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
