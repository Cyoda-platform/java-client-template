package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OrderCanBeFulfilledCriterion - Checks if all order line items can be fulfilled based on current product stock.
 * 
 * This criterion is used to validate order before fulfillment and prevent fulfillment of unfulfillable orders.
 * It evaluates whether all products in the order have sufficient stock for their ordered quantities.
 */
@Component
public class OrderCanBeFulfilledCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public OrderCanBeFulfilledCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Order can be fulfilled criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderCanBeFulfilled)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that all order line items can be fulfilled based on current product stock
     */
    private EvaluationOutcome validateOrderCanBeFulfilled(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if order is null (structural validation)
        if (order == null) {
            logger.warn("Order is null");
            return EvaluationOutcome.fail("Order is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if order has lines
        if (order.getLines() == null || order.getLines().isEmpty()) {
            logger.warn("Order has no lines");
            return EvaluationOutcome.fail("Order has no line items", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check each line item for stock availability
        for (Order.OrderLine line : order.getLines()) {
            if (line.getSku() == null || line.getSku().trim().isEmpty()) {
                logger.warn("Order line has null or empty SKU");
                return EvaluationOutcome.fail("Order line has invalid SKU", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            if (line.getQty() == null || line.getQty() <= 0) {
                logger.warn("Order line has invalid quantity for SKU: {}", line.getSku());
                return EvaluationOutcome.fail("Order line has invalid quantity for SKU: " + line.getSku(), 
                                            StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // Get product for read-only access
            try {
                ModelSpec productModelSpec = new ModelSpec()
                        .withName(Product.ENTITY_NAME)
                        .withVersion(Product.ENTITY_VERSION);
                
                EntityWithMetadata<Product> productWithMetadata = entityService.findByBusinessId(
                        productModelSpec, line.getSku(), "sku", Product.class);
                
                if (productWithMetadata == null) {
                    logger.warn("Product not found for SKU: {}", line.getSku());
                    return EvaluationOutcome.fail("Product not found for SKU: " + line.getSku(),
                                                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }

                Product product = productWithMetadata.entity();
                
                if (product.getQuantityAvailable() == null) {
                    logger.warn("Product quantity is null for SKU: {}", line.getSku());
                    return EvaluationOutcome.fail("Product quantity not set for SKU: " + line.getSku(), 
                                                StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }

                if (product.getQuantityAvailable() < line.getQty()) {
                    logger.debug("Insufficient stock for SKU: {}, available: {}, required: {}", 
                               line.getSku(), product.getQuantityAvailable(), line.getQty());
                    return EvaluationOutcome.fail("Insufficient stock for SKU: " + line.getSku() + 
                                                " (available: " + product.getQuantityAvailable() + 
                                                ", required: " + line.getQty() + ")", 
                                                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }

            } catch (Exception e) {
                logger.error("Error checking stock for SKU: {}", line.getSku(), e);
                return EvaluationOutcome.fail("Error checking stock for SKU: " + line.getSku(),
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        logger.debug("Order can be fulfilled validation passed");
        return EvaluationOutcome.success();
    }
}
