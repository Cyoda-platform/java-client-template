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
 * ProductHasStockCriterion - Checks if product has sufficient stock for requested quantity.
 * 
 * This criterion is used to validate stock before adding to cart and prevent overselling.
 * It evaluates whether the product has enough quantityAvailable for the requested amount.
 * 
 * Note: The requested quantity should be provided in the request context or payload.
 * For this implementation, we'll check if the product has any stock available (> 0).
 */
@Component
public class ProductHasStockCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProductHasStockCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Product has stock criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Product.class, this::validateProductHasStock)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that the product has sufficient stock
     * 
     * Note: In a more complex implementation, the requested quantity would be extracted
     * from the request context or payload. For this basic implementation, we check
     * if the product has any stock available.
     */
    private EvaluationOutcome validateProductHasStock(CriterionSerializer.CriterionEntityEvaluationContext<Product> context) {
        Product product = context.entityWithMetadata().entity();

        // Check if product is null (structural validation)
        if (product == null) {
            logger.warn("Product is null");
            return EvaluationOutcome.fail("Product is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if quantityAvailable is null
        if (product.getQuantityAvailable() == null) {
            logger.warn("Product quantityAvailable is null for SKU: {}", product.getSku());
            return EvaluationOutcome.fail("Product quantity is not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if product has stock available
        if (product.getQuantityAvailable() <= 0) {
            logger.debug("Product has no stock available for SKU: {}, quantity: {}", 
                        product.getSku(), product.getQuantityAvailable());
            return EvaluationOutcome.fail("Product is out of stock (available: " + product.getQuantityAvailable() + ")", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Product has stock validation passed for SKU: {}, available: {}", 
                    product.getSku(), product.getQuantityAvailable());
        return EvaluationOutcome.success();
    }
}
