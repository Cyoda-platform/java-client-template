package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderReadyForPickingCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    @Autowired
    private EntityService entityService;

    public OrderReadyForPickingCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking OrderReadyForPickingCriterion for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entity();
        
        logger.info("Validating order ready for picking: {}", order != null ? order.getOrderId() : "null");

        // Check if order entity exists
        if (order == null) {
            logger.warn("Order entity not found");
            return EvaluationOutcome.fail("Order entity not found", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Note: State validation is handled by the workflow system
        // This criterion focuses on business logic validation

        // Check if order.lines is not empty
        if (order.getLines() == null || order.getLines().isEmpty()) {
            logger.warn("Order must have line items");
            return EvaluationOutcome.fail("Order must have line items", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if order.guestContact.address is complete
        if (order.getGuestContact() == null || order.getGuestContact().getAddress() == null) {
            logger.warn("Order must have complete guest contact with address");
            return EvaluationOutcome.fail("Incomplete shipping address", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        Order.Address address = order.getGuestContact().getAddress();
        if (isNullOrEmpty(address.getLine1()) || 
            isNullOrEmpty(address.getCity()) || 
            isNullOrEmpty(address.getPostcode()) || 
            isNullOrEmpty(address.getCountry())) {
            logger.warn("Incomplete shipping address - missing required fields");
            return EvaluationOutcome.fail("Incomplete shipping address", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check stock availability for each order line
        for (Order.OrderLine orderLine : order.getLines()) {
            try {
                // Find Product by sku
                SearchConditionRequest condition = SearchConditionRequest.group("and",
                    Condition.of("sku", "equals", orderLine.getSku()));
                
                var productResponse = entityService.getFirstItemByCondition(Product.class, condition, false);
                
                if (productResponse.isPresent()) {
                    Product product = productResponse.get().getData();
                    
                    // Check if Product.quantityAvailable >= 0 (should not be negative)
                    if (product.getQuantityAvailable() < 0) {
                        logger.warn("Insufficient stock for product: {}. Available: {}", 
                                   orderLine.getSku(), product.getQuantityAvailable());
                        return EvaluationOutcome.fail("Insufficient stock for product: " + orderLine.getSku(), 
                                                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                    }
                } else {
                    logger.warn("Product not found for SKU: {}", orderLine.getSku());
                    return EvaluationOutcome.fail("Product not found for SKU: " + orderLine.getSku(), 
                                                StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
                
            } catch (Exception e) {
                logger.error("Error checking stock for product: {}", orderLine.getSku(), e);
                return EvaluationOutcome.fail("Error checking stock for product: " + orderLine.getSku(), 
                                            StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        logger.info("Order validation successful for order: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
