package com.java_template.application.criterion;

import com.java_template.application.entity.order_entity.version_1.OrderEntity;
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

import java.time.LocalDateTime;

/**
 * OrderValidityCriterion - Check if imported order data is valid
 * Transition: validate_order (imported → validated)
 */
@Component
public class OrderValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking OrderValidity criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(OrderEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validate order data integrity and business rules
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<OrderEntity> context) {
        OrderEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null
        if (entity == null) {
            logger.warn("OrderEntity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if order ID is present
        if (entity.getOrderId() == null) {
            logger.warn("Order ID is null");
            return EvaluationOutcome.fail("Order ID is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if pet ID is present
        if (entity.getPetId() == null) {
            logger.warn("Pet ID is null for order: {}", entity.getOrderId());
            return EvaluationOutcome.fail("Pet ID is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if quantity is valid
        if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
            logger.warn("Invalid quantity for order {}: {}", entity.getOrderId(), entity.getQuantity());
            return EvaluationOutcome.fail("Quantity must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if unit price is valid
        if (entity.getUnitPrice() == null || entity.getUnitPrice() < 0) {
            logger.warn("Invalid unit price for order {}: {}", entity.getOrderId(), entity.getUnitPrice());
            return EvaluationOutcome.fail("Unit price must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if total amount is valid
        if (entity.getTotalAmount() == null || entity.getTotalAmount() < 0) {
            logger.warn("Invalid total amount for order {}: {}", entity.getOrderId(), entity.getTotalAmount());
            return EvaluationOutcome.fail("Total amount must be non-negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if total amount matches calculation
        double expectedTotal = entity.getQuantity() * entity.getUnitPrice();
        if (Math.abs(entity.getTotalAmount() - expectedTotal) > 0.01) {
            logger.warn("Total amount mismatch for order {}: expected {}, got {}", 
                    entity.getOrderId(), expectedTotal, entity.getTotalAmount());
            return EvaluationOutcome.fail("Total amount does not match quantity × unit price", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if order date is present
        if (entity.getOrderDate() == null) {
            logger.warn("Order date is null for order: {}", entity.getOrderId());
            return EvaluationOutcome.fail("Order date is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if order date is not in the future
        if (entity.getOrderDate().isAfter(LocalDateTime.now())) {
            logger.warn("Order date is in the future for order {}: {}", entity.getOrderId(), entity.getOrderDate());
            return EvaluationOutcome.fail("Order date cannot be in the future", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order validation passed for order: {}", entity.getOrderId());
        return EvaluationOutcome.success();
    }
}
