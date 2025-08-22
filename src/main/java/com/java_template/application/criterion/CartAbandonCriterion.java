package com.java_template.application.criterion;

import com.java_template.application.entity.cart.version_1.Cart;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@Component
public class CartAbandonCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CartAbandonCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Cart.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Cart> context) {
         Cart cart = context.entity();

         // Basic validation: id presence is required for logging context — but do not invent properties.
         String cartId = cart != null ? cart.getId() : null;

         if (cart == null) {
             logger.warn("Cart entity is null in CartAbandonCriterion invocation");
             return EvaluationOutcome.fail("Cart entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure status exists
         if (cart.getStatus() == null || cart.getStatus().isBlank()) {
            logger.warn("Cart {} has missing status", cartId);
            return EvaluationOutcome.fail("Cart status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Only Active carts are eligible for abandonment evaluation
         if (!"Active".equalsIgnoreCase(cart.getStatus())) {
             logger.debug("Cart {} not in Active state (status='{}'), skipping abandonment evaluation", cartId, cart.getStatus());
             return EvaluationOutcome.fail("Cart not Active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ensure there are items to abandon (no need to abandon empty carts)
         if (cart.getItems() == null || cart.getItems().isEmpty()) {
             logger.debug("Cart {} has no items, not eligible for abandonment", cartId);
             return EvaluationOutcome.fail("Cart has no items", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Use createdAt as the last-known timestamp (entity lacks lastUpdated)
         if (cart.getCreatedAt() == null || cart.getCreatedAt().isBlank()) {
             logger.warn("Cart {} has missing createdAt", cartId);
             return EvaluationOutcome.fail("Cart createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         OffsetDateTime createdAt;
         try {
             createdAt = OffsetDateTime.parse(cart.getCreatedAt());
         } catch (DateTimeParseException ex) {
             logger.warn("Cart {} has invalid createdAt format: {}", cartId, cart.getCreatedAt());
             return EvaluationOutcome.fail("createdAt has invalid format", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: mark eligible for abandonment if inactivity exceeds threshold.
         // Threshold chosen: 48 hours (2 days) of inactivity based on workflow timeout semantics.
         Duration inactivityThreshold = Duration.ofHours(48);
         OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

         if (createdAt.isBefore(nowUtc.minus(inactivityThreshold))) {
             logger.info("Cart {} eligible for abandonment (createdAt={}, now={}, thresholdHours={})", cartId, cart.getCreatedAt(), nowUtc.toString(), inactivityThreshold.toHours());
             return EvaluationOutcome.success();
         } else {
             logger.debug("Cart {} not yet eligible for abandonment (createdAt={}, now={}, thresholdHours={})", cartId, cart.getCreatedAt(), nowUtc.toString(), inactivityThreshold.toHours());
             return EvaluationOutcome.fail("Cart activity within abandonment threshold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
    }
}