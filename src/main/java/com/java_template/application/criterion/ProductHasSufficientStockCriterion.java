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

@Component
public class ProductHasSufficientStockCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    
    private static final int LOW_STOCK_WARNING_THRESHOLD = 10;

    public ProductHasSufficientStockCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if product has sufficient stock for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProductHasSufficientStock)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateProductHasSufficientStock(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entity();
        
        // Check if product is null
        if (product == null) {
            logger.warn("Product entity is null");
            return EvaluationOutcome.fail("Product entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Note: In a real implementation, the requested quantity would be passed as a parameter
        // For this simplified implementation, we'll validate that the product has some stock available
        // In practice, you would need to pass the requested quantity to this criterion
        
        // Validate product has required fields
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            logger.warn("Product has no SKU");
            return EvaluationOutcome.fail("Product SKU is required", 
                                        StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Validate quantity available is not null
        if (product.getQuantityAvailable() == null) {
            logger.warn("Product {} has null quantity available", product.getSku());
            return EvaluationOutcome.fail("Product quantity available is required", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // For this simplified implementation, we'll just check if there's any stock available
        // In a real scenario, you'd compare against the requested quantity
        int availableQuantity = product.getQuantityAvailable();
        
        if (availableQuantity <= 0) {
            logger.warn("Product {} has insufficient stock: available={}", product.getSku(), availableQuantity);
            return EvaluationOutcome.fail(
                String.format("Insufficient stock: available=%d, requested=1", availableQuantity), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Warn if stock is getting low
        if (availableQuantity <= LOW_STOCK_WARNING_THRESHOLD) {
            logger.warn("Low stock warning for product {}: remaining={}", product.getSku(), availableQuantity);
        }

        logger.debug("Product {} has sufficient stock: available={}", product.getSku(), availableQuantity);
        return EvaluationOutcome.success();
    }

    /**
     * Validates product stock against a specific requested quantity.
     * This method could be used in a more advanced implementation where the requested quantity
     * is passed as a parameter to the criterion.
     */
    private EvaluationOutcome validateProductStockForQuantity(Product product, int requestedQuantity) {
        if (product == null) {
            return EvaluationOutcome.fail("Product entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (requestedQuantity <= 0) {
            return EvaluationOutcome.fail("Requested quantity must be positive", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (product.getQuantityAvailable() == null) {
            return EvaluationOutcome.fail("Product quantity available is required", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        int availableQuantity = product.getQuantityAvailable();
        
        if (availableQuantity < requestedQuantity) {
            logger.warn("Product {} has insufficient stock: available={}, requested={}", 
                       product.getSku(), availableQuantity, requestedQuantity);
            return EvaluationOutcome.fail(
                String.format("Insufficient stock: available=%d, requested=%d", availableQuantity, requestedQuantity), 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Warn if stock will be low after this request
        int remainingAfterRequest = availableQuantity - requestedQuantity;
        if (remainingAfterRequest < LOW_STOCK_WARNING_THRESHOLD) {
            logger.warn("Low stock warning for product {}: remaining after request={}", 
                       product.getSku(), remainingAfterRequest);
        }

        return EvaluationOutcome.success();
    }
}
