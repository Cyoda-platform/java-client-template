package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * OrderCancellationCriterion - Determines if order can be cancelled
 * Transition: PENDING/CONFIRMED → CANCELLED
 */
@Component
public class OrderCancellationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Cancellation time window in minutes
    private static final long CANCELLATION_WINDOW_MINUTES = 5;

    public OrderCancellationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking order cancellation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderCancellation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderCancellation(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().getState();

        // Check if entity is null
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Orders can be cancelled if not yet being prepared
        if (!"PENDING".equals(currentState) && !"CONFIRMED".equals(currentState)) {
            return EvaluationOutcome.fail("Order can only be cancelled when PENDING or CONFIRMED", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check cancellation time window
        if (order.getCreatedAt() == null) {
            return EvaluationOutcome.fail("Order creation time is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        LocalDateTime now = LocalDateTime.now();
        Duration timeSinceCreation = Duration.between(order.getCreatedAt(), now);

        if ("PENDING".equals(currentState)) {
            // Always allow cancellation of pending orders
            logger.debug("Order cancellation allowed for PENDING order: {}", order.getOrderId());
            return EvaluationOutcome.success();
        }

        if ("CONFIRMED".equals(currentState)) {
            // Check if within cancellation time window
            if (timeSinceCreation.toMinutes() <= CANCELLATION_WINDOW_MINUTES) {
                logger.debug("Order cancellation allowed within time window for CONFIRMED order: {}", order.getOrderId());
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Order can only be cancelled within " + CANCELLATION_WINDOW_MINUTES + " minutes of confirmation", 
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }

        return EvaluationOutcome.fail("Order cannot be cancelled in current state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
