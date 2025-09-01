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

import java.math.BigDecimal;

@Component
public class ProductAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking ProductAvailableCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entity();
        
        logger.info("Validating product availability: {}", product != null ? product.getSku() : "null");

        // Check if product entity exists
        if (product == null) {
            logger.warn("Product not found");
            return EvaluationOutcome.fail("Product not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Note: This criterion is designed to validate product availability for a requested quantity
        // However, the requested quantity would typically come from the request context or parameters
        // For this implementation, we'll validate general product availability
        
        // Check if product.quantityAvailable >= 0 (basic availability check)
        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            logger.warn("Product has invalid quantity available: {}", product.getQuantityAvailable());
            return EvaluationOutcome.fail("Insufficient stock available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if product.price > 0
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Invalid product price: {}", product.getPrice());
            return EvaluationOutcome.fail("Invalid product price", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if product.category is not null/empty
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            logger.warn("Product must have a category");
            return EvaluationOutcome.fail("Product must have a category", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Additional validation: check if product name and SKU are valid
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            logger.warn("Product must have a valid SKU");
            return EvaluationOutcome.fail("Product must have a valid SKU", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (product.getName() == null || product.getName().trim().isEmpty()) {
            logger.warn("Product must have a valid name");
            return EvaluationOutcome.fail("Product must have a valid name", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Product validation successful for product: {} - available qty: {}", 
                   product.getSku(), product.getQuantityAvailable());
        return EvaluationOutcome.success();
    }

    /**
     * Overloaded validation method that accepts a requested quantity for more specific validation.
     * This could be used when the criterion is called with additional context parameters.
     */
    public EvaluationOutcome validateEntityWithQuantity(Product product, Integer requestedQuantity) {
        // Additional validation for requested quantity
        if (requestedQuantity != null && requestedQuantity > 0) {
            if (product.getQuantityAvailable() < requestedQuantity) {
                logger.warn("Insufficient stock for product: {} - available: {}, requested: {}",
                           product.getSku(), product.getQuantityAvailable(), requestedQuantity);
                return EvaluationOutcome.fail("Insufficient stock available", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
